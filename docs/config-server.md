# Config Server

## Architecture

`config-server` is a thin wrapper around Spring Cloud Config Server backed by the **native** (filesystem) profile — it serves YAML files bundled in its own `src/main/resources/config-repo/` directory rather than a git remote. Every other OrderFlow service (except itself) resolves its externalized configuration from it on boot, in addition to its own local `application.yml`.

Resolution order per client, highest priority first:
1. Environment variables / command-line args (standard Spring Boot behavior)
2. Config Server's `{application-name}.yml` (e.g. `identity-service.yml`)
3. Config Server's shared `application.yml` (defaults for every client)
4. The client's own local `application.yml`

This is why `server.port` lives in `config-repo/identity-service.yml` and not in `identity-service/src/main/resources/application.yml` anymore — it's now centrally owned, and changing it means editing one file that governs every environment, not eight.

```mermaid
flowchart LR
    subgraph Repo["config-server (native backend)"]
        AppYml["application.yml (shared defaults)"]
        SvcYml["identity-service.yml, product-service.yml, ..."]
    end
    Client["Any OrderFlow client\n(identity-service, order-service, ...)"]
    Client -- "1: spring.config.import=configserver:...\n(Basic Auth)" --> Repo
    Repo -- "2: merged JSON environment\n(service-specific over shared)" --> Client
    Client -- "3: boots with merged config" --> Client
```

## Sequence: Client Boot

```mermaid
sequenceDiagram
    participant Client as identity-service
    participant Config as config-server :8888

    Client->>Client: Load local application.yml (spring.application.name, config.import)
    Client->>Config: GET /identity-service/default (Basic Auth)
    Config->>Config: Read config-repo/identity-service.yml
    Config->>Config: Read config-repo/application.yml (shared)
    Config-->>Client: 200 OK — merged PropertySources JSON
    Client->>Client: Bind merged properties into Environment
    Client->>Client: Continue Spring Boot startup (server.port now known)
```

If `config-server` is unreachable, `spring.config.import=optional:configserver:...` (note the `optional:` prefix) lets the client boot anyway using only its local `application.yml` — deliberately chosen so any single service stays runnable in isolation, matching the "run any service on its own" property established in Milestone 1.

## API

Spring Cloud Config Server's standard environment endpoint: `GET /{application}/{profile}[/{label}]`.

**Request**
```
GET /identity-service/default HTTP/1.1
Host: localhost:8888
Authorization: Basic Y29uZmlnLWNsaWVudDpjb25maWctY2xpZW50LXNlY3JldA==
```

**Response** (abbreviated)
```json
{
  "name": "identity-service",
  "profiles": ["default"],
  "propertySources": [
    {
      "name": "class path resource [config-repo/identity-service.yml] via location 'classpath:/config-repo/'",
      "source": {
        "server.port": 8081,
        "management.health.redis.enabled": false
      }
    },
    {
      "name": "class path resource [config-repo/application.yml] via location 'classpath:/config-repo/'",
      "source": {
        "management.endpoints.web.exposure.include": "health,info",
        "logging.pattern.level": "%5p [${spring.application.name:-},%X{correlationId:-}]"
      }
    }
  ]
}
```
Property sources are ordered most-specific-first — a client merges them top-to-bottom, so `identity-service.yml` wins over the shared `application.yml` on any key collision.

### Sample curl / Postman requests

```bash
# Health check — no auth required
curl http://localhost:8888/actuator/health

# Fetch a service's merged config — requires Basic Auth
curl -u config-client:config-client-secret http://localhost:8888/identity-service/default

# Unauthenticated config request — expect 401
curl -i http://localhost:8888/identity-service/default
```
For Postman: set the request to `GET http://localhost:8888/identity-service/default`, Authorization tab → Basic Auth → username `config-client`, password `config-client-secret` (or your `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` overrides).

## Database Schema / Event Flow

Not applicable — `config-server` owns no database and publishes/consumes no Kafka events.

## Operational Note: Phase-Appropriate Health Indicators

Live-booting `identity-service` against this Config Server surfaced two things worth knowing before touching `config-repo/`:

1. **Auto-configuration doesn't wait for you.** Business services carry `spring-boot-starter-data-jpa` + `postgresql` + Flyway on the classpath, per the intended tech stack, well before any of them has a real datasource. Spring Boot doesn't treat "the dependency is there but unconfigured" as a no-op — it tries to build a `DataSource`/`EntityManagerFactory`/run Flyway migrations at startup and crashes without one. Each affected service's local `application.yml` excludes those auto-configurations until its own DB-wiring phase (roadmap items 7–12) lands — removed for `identity-service` (item 7) and `product-service` (item 8) now that both have one; still present for the remaining 4.
2. **Actuator health reflects whatever indicators are on the classpath, whether or not you're ready for them.** `spring-cloud-starter-netflix-eureka-client` and `spring-boot-starter-data-redis` auto-register health indicators that ping Eureka/Redis. `config-repo/{service}.yml` disabled both (`management.health.eureka.enabled` / `management.health.redis.enabled`) while their infrastructure was unreachable. Now that Discovery Server (item 5) is wired and running, the Eureka override has been removed from every `config-repo/*.yml` — only `management.health.redis.enabled: false` remains, until Redis (item 14) lands.

Logged here rather than silently baked in because it's exactly the kind of thing that's easy to forget to revert — grep `config-repo/` for `enabled: false` when Redis (item 14) lands, same as was just done for Eureka.

## Security

- HTTP Basic Auth on every endpoint except `/actuator/health` and `/actuator/info` (see `SecurityConfig`), so container/orchestrator health probes work without credentials.
- Credentials come from `spring.security.user.name`/`password`, sourced from `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` env vars — the in-code defaults (`config-client` / `config-client-secret`) are **dev-only** and must be overridden in any real deployment.
- Config is currently plaintext YAML in the jar. That was a low-stakes limitation while the only things living here were internal Basic Auth credentials (this server's own, Discovery Server's) — it's a real one now that `identity-service` centralizes its Postgres password and JWT signing secret the same way (see `docs/identity-service.md`). The standard next step, still not implemented, is Spring Cloud Config's encryption support (`{cipher}...` values) backed by a proper key/HSM, not committing plaintext secrets — required before any real deployment.

## Interview Notes

- **Why native over git for this stage?** Git-backed Config Server is the production-standard choice (versioned, auditable, rollback-able via `git revert`), but it requires an actual git remote. Native is a legitimate, supported backend for local/dev — the switch later is a config-only change (`spring.cloud.config.server.git.uri=...`), no code change, since both backends implement the same `EnvironmentRepository` contract.
- **Why `optional:` on `spring.config.import`?** Without it, a client that can't reach Config Server fails to boot at all — a hard dependency on infrastructure that, in local dev, you might not have started yet. `optional:` trades strict fail-fast for developer ergonomics; in a real prod rollout you'd likely want the non-optional form (or a readiness gate) so a service never silently runs with incomplete config.
- **Why is discovery-based lookup (`spring.cloud.config.discovery.enabled=true`) not used?** It would let clients find Config Server via Eureka instead of a hardcoded host:port — but Eureka registration is the next roadmap item, not yet built. Hardcoding now, switching to discovery-based lookup once Eureka is live, is a one-property change.
- **Why is `spring.application.name` never centralized?** Bootstrapping problem: Config Server needs to know *which* application is asking before it can return that application's config, so the name has to be known locally before the config-import step runs.
