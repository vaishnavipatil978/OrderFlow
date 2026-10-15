# Discovery Server

## Architecture

`discovery-server` is a standalone (non-clustered) Netflix Eureka server. Every OrderFlow service except itself and `config-server` registers here on boot, so they can later find each other by logical name (`IDENTITY-SERVICE`, `PRODUCT-SERVICE`, ...) instead of hardcoded `host:port` pairs — this is what `api-gateway`'s `lb://SERVICE-NAME` routes and OpenFeign's client-side load balancing will both depend on once those pieces are built.

Deliberate configuration choices, all in `config-repo/discovery-server.yml`:
- `register-with-eureka: false`, `fetch-registry: false` — the server doesn't act as its own client. Standard for a standalone dev instance; a peer-aware HA cluster would flip both to `true` and point each node at its peers.
- `enable-self-preservation: false` — self-preservation exists to stop Eureka from evicting a large fraction of instances at once when it suspects a network partition rather than genuinely dead instances. That protection matters when you have enough instances for "suspiciously many heartbeats missing at once" to be a meaningful signal. With one dev instance of everything, it only delays noticing something actually died — so it's off here and should be reconsidered (turned back on) for any real multi-instance deployment.

```mermaid
flowchart TB
    subgraph Registry["discovery-server :8761 (standalone)"]
        Reg[(In-memory registry)]
    end

    Gateway["api-gateway"] -- register + heartbeat --> Registry
    Identity["identity-service"] -- register + heartbeat --> Registry
    Product["product-service"] -- register + heartbeat --> Registry
    Order["order-service"] -- register + heartbeat --> Registry
    Payment["payment-service"] -- register + heartbeat --> Registry
    Inventory["inventory-service"] -- register + heartbeat --> Registry
    Notification["notification-service"] -- register + heartbeat --> Registry

    Order -. "future: lookup PRODUCT-SERVICE" .-> Registry
    Gateway -. "future: lookup lb://ORDER-SERVICE" .-> Registry
```

## Sequence: Client Registration & Heartbeat

```mermaid
sequenceDiagram
    participant Client as identity-service
    participant Eureka as discovery-server :8761

    Client->>Client: Boot; eureka.client.service-url.defaultZone resolved (from config-server)
    Client->>Eureka: POST /eureka/apps/IDENTITY-SERVICE (Basic Auth, instance metadata)
    Eureka-->>Client: 204 No Content (registered, initial status STARTING)
    loop every 30s (default lease renewal)
        Client->>Eureka: PUT /eureka/apps/IDENTITY-SERVICE/{instanceId} (heartbeat)
        Eureka-->>Client: 200 OK
    end
    Note over Eureka: If no heartbeat for 90s (default eviction timeout),<br/>instance is evicted from the registry
    Client->>Eureka: DELETE /eureka/apps/IDENTITY-SERVICE/{instanceId} (graceful shutdown)
```

Because `eureka.client.healthcheck.enabled: true` is set (in the shared `config-repo/application.yml`), the status Eureka shows isn't just "process is running" — it reflects the client's actual Spring Boot Actuator health once that becomes a meaningful signal (once a service has real dependencies to check).

## API

Standard Eureka REST API (subset relevant here):

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | HTML dashboard — registered instances, their status, uptime |
| `GET` | `/eureka/apps` | Full registry as XML or JSON (`Accept: application/json`) |
| `GET` | `/eureka/apps/{APP-NAME}` | Instances for a single application |
| `POST` | `/eureka/apps/{APP-NAME}` | Register an instance (done automatically by Eureka clients, not called manually) |
| `PUT` | `/eureka/apps/{APP-NAME}/{instanceId}` | Heartbeat/lease renewal (automatic) |
| `DELETE` | `/eureka/apps/{APP-NAME}/{instanceId}` | Deregister (automatic on graceful shutdown) |

### Sample curl requests

```bash
# Health check - no auth required
curl http://localhost:8761/actuator/health

# Full registry as JSON - requires Basic Auth
curl -u eureka-client:eureka-client-secret -H "Accept: application/json" http://localhost:8761/eureka/apps

# Dashboard (browser) - will prompt for Basic Auth credentials
open http://localhost:8761/
```
For Postman: `GET http://localhost:8761/eureka/apps`, header `Accept: application/json`, Authorization tab → Basic Auth → `eureka-client` / `eureka-client-secret` (or your `EUREKA_USERNAME`/`EUREKA_PASSWORD` overrides).

## Database Schema / Event Flow

Not applicable — `discovery-server` holds its registry in memory only (no database) and publishes/consumes no Kafka events.

## Security

- HTTP Basic Auth on everything except `/actuator/health` and `/actuator/info` — same posture as Config Server, since the registry discloses internal service topology (names, hosts, ports).
- Eureka's HTTP client has no separate username/password property; the *only* way a registering client authenticates is by embedding credentials directly in `eureka.client.service-url.defaultZone` (`http://user:pass@host:port/eureka/`), which is why that URL looks the way it does in `config-repo/application.yml`.
- Credentials: `EUREKA_USERNAME`/`EUREKA_PASSWORD` env vars, dev-only defaults `eureka-client`/`eureka-client-secret` baked in for local use — same pattern, same warning, as Config Server's credentials.
- Unlike Config Server (which can't fetch its own config), Discovery Server *is* a Config Client, so its `spring.security.user.*` lives in `config-repo/discovery-server.yml` — centralized rather than duplicated locally.

## Interview Notes

- **AP vs. CP registry (CAP theorem).** Eureka is deliberately AP — under a network partition, it prefers to keep serving a (possibly stale) registry over refusing to answer. That's why it favors client-side caching and lease expiry over strict consistency: a service directory that's briefly stale is far less damaging than one that's unavailable. Contrast with something like ZooKeeper/Consul in CP mode, which would rather refuse reads than risk serving inconsistent data.
- **Why self-preservation exists and why it's off here.** It's a safety valve against "the network is lying to me" — when heartbeat loss crosses a threshold across *many* instances at once, that's more likely a partition than mass simultaneous death, so Eureka stops evicting until things stabilize. With a single dev instance of everything, that signal is meaningless noise, so it's disabled — but this is exactly the kind of flag that must be revisited before any real multi-instance rollout.
- **Why standalone, not peer-aware, at this stage.** A peer-aware cluster (2+ Eureka nodes replicating to each other) is the actual production shape — it removes the registry itself as a single point of failure. Standalone is the right *starting* choice for local/dev: less to run, less to reason about, and the migration path (add peer nodes, flip `register-with-eureka`/`fetch-registry` to `true`, point each at the others) doesn't change how any client behaves.
- **Why credentials live in the URL, not a property.** This trips people up the first time: most Spring Cloud clients (Config Server included) have first-class `username`/`password` properties. Eureka's registration client doesn't — it's a plain HTTP client hitting a REST API, so Basic Auth has to be embedded in the URL like any other HTTP client would do it.
- **`prefer-ip-address: true`.** Without it, Eureka advertises instances by hostname, which is fragile in containerized environments where hostnames are often random container IDs that mean nothing outside the container network. Advertising by IP avoids a DNS-resolution dependency for service-to-service calls later. Set once, centrally, in `config-repo/application.yml` — it applies uniformly to every registering client.
