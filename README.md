# OrderFlow — Enterprise Order Management Platform

OrderFlow is a microservices-based backend for a complete order-management lifecycle: user registration/login, product catalog & search, inventory management, order placement, payments, cancellation, order tracking, and notifications.

This is a **backend-only** platform — there is no frontend, and none is planned. It's built to resemble a real internal enterprise codebase, not a tutorial project.

## Table of Contents

- [Status](#status)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Module Map](#module-map)
- [Common Library](#common-library)
- [Config Server](#config-server)
- [Discovery Server](#discovery-server)
- [API Gateway](#api-gateway)
- [Identity Service](#identity-service)
- [Product Service](#product-service)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Running the Platform](#running-the-platform)
- [Verifying a Service Is Up](#verifying-a-service-is-up)
- [Project Structure](#project-structure)
- [Engineering Standards](#engineering-standards)
- [Why This Foundation Looks the Way It Does](#why-this-foundation-looks-the-way-it-does)
- [Build Order / Roadmap](#build-order--roadmap)
- [Troubleshooting](#troubleshooting)

## Status

**Milestone 1 complete: Maven multi-module project structure.**
**Milestone 2 complete: shared conventions (`common-library`).**
**Milestone 3 complete: Config Server.**
**Milestone 4 complete: Discovery Server (Eureka).**
**Milestone 5 complete: API Gateway.**
**Milestone 6 complete: Identity Service (JWT auth & RBAC).**
**Milestone 7 complete: Product Service (catalog & search).**
**Milestone 8 complete: Inventory, Payment, Order (saga + outbox), Notification (Kafka), Redis product cache.** See [`docs/phase-4.md`](docs/phase-4.md) for what was verified live and what was not.

Every service module exists with its dependency-managed `pom.xml`, a bootstrap `@SpringBootApplication` class, an `application.yml`, and the full production package skeleton. The six business services depend on `common-library`, which supplies a generic API response envelope, a global exception handler, correlation-ID propagation, a JPA base entity, and (as of Product Service) shared JWT validation — see [Common Library](#common-library). Every service except `config-server` itself now pulls its externalized config (`server.port`, and now Eureka connection details) from `config-server` on boot — see [Config Server](#config-server). `api-gateway` and the 6 business services now explicitly register with `discovery-server` on boot instead of relying on Spring Cloud's undocumented default — see [Discovery Server](#discovery-server). `api-gateway` now load-balances real routes to all 6 business services via Eureka, wraps each in a Resilience4j circuit breaker, and generates/propagates a correlation ID for every request at the edge — see [API Gateway](#api-gateway). `identity-service` is the first business service with real functionality and a real database: registration, login, JWT issuance, refresh-token rotation, logout, and RBAC (`CUSTOMER`/`ADMIN`) — see [Identity Service](#identity-service). `product-service` is the second: catalog CRUD + paginated search, and the first service besides `identity-service` to validate the JWTs it issues — see [Product Service](#product-service). **The other 4 business services still have no business logic yet, and no Kafka wiring or Docker setup exists yet** — those are added module-by-module, in the order in [Build Order / Roadmap](#build-order--roadmap), each going through Design → Implementation → Review → Testing before the next one starts.

What that means practically today: every service **builds** and **boots** (Spring context starts, Actuator health endpoint responds), pulling its port and Eureka config from `config-server` if it's running (or falling back to booting standalone, since the import is `optional:`); registering with `discovery-server` if it's running; every service that throws a `BusinessException` or validation error already gets the standardized error envelope; a request through `api-gateway` to any of the 4 still-empty business services gets a clean `503` fallback with a correlation ID (there's nothing behind those routes to reach yet); and `identity-service` + `product-service` both actually process real traffic end to end against real Postgres databases, with `product-service` independently verifying tokens `identity-service` issued — see their respective sections for what that covers.

## Architecture

```mermaid
flowchart TB
    Client["Client (Postman / curl / any HTTP client)"]

    subgraph Edge["Edge"]
        Gateway["api-gateway :8080"]
    end

    subgraph Infra["Infrastructure"]
        Config["config-server :8888"]
        Discovery["discovery-server (Eureka) :8761"]
    end

    subgraph Services["Business Services"]
        Identity["identity-service :8081"]
        Product["product-service :8082"]
        Inventory["inventory-service :8083"]
        Order["order-service :8084"]
        Payment["payment-service :8085"]
        Notification["notification-service :8086"]
    end

    subgraph Data["Per-Service Data"]
        DBI[(orderflow_identity)]
        DBP[(orderflow_product)]
        DBInv[(orderflow_inventory)]
        DBO[(orderflow_order)]
        DBPay[(orderflow_payment)]
        DBN[(orderflow_notification)]
        Redis[(Redis Cache)]
        Kafka[(Kafka)]
    end

    Client --> Gateway
    Gateway --> Identity
    Gateway --> Product
    Gateway --> Inventory
    Gateway --> Order
    Gateway --> Payment
    Gateway --> Notification

    Services -.registers with.-> Discovery
    Gateway -.discovers via.-> Discovery
    Services -.pulls config from.-> Config

    Order -- OpenFeign --> Product
    Order -- OpenFeign --> Inventory
    Order -- OpenFeign --> Payment

    Order -- Kafka events --> Kafka
    Payment -- Kafka events --> Kafka
    Inventory -- Kafka events --> Kafka
    Kafka -- consumes --> Notification

    Identity --> DBI
    Product --> DBP
    Inventory --> DBInv
    Order --> DBO
    Payment --> DBPay
    Notification --> DBN

    Order --- Redis
    Product --- Redis
```

No service ever talks to another service's database directly. Synchronous calls go through OpenFeign (service-to-service REST); asynchronous, decoupled communication goes through Kafka.

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5, Spring Security, Spring Data JPA, Spring Validation |
| Service mesh (Spring Cloud) | Config Server, Eureka Discovery, API Gateway, OpenFeign |
| Messaging | Apache Kafka |
| Database | PostgreSQL (one schema per service) |
| Cache | Redis |
| Build | Maven (multi-module) |
| Infra | Docker, Docker Compose *(added in a later phase)* |
| Libraries | Flyway, Lombok, MapStruct, JJWT, Resilience4j, Micrometer, Spring Boot Actuator, springdoc-openapi |

## Module Map

| Module | Role | Port | Owns DB |
|---|---|---|---|
| `config-server` | Centralized Spring Cloud Config | 8888 | — |
| `discovery-server` | Eureka service registry | 8761 | — |
| `api-gateway` | Reactive edge gateway (Spring Cloud Gateway) | 8080 | — |
| `identity-service` | Registration/login, JWT issuance, RBAC | 8081 | `orderflow_identity` |
| `product-service` | Product catalog & search | 8082 | `orderflow_product` |
| `inventory-service` | Stock levels & reservation | 8083 | `orderflow_inventory` |
| `order-service` | Order lifecycle, saga orchestration | 8084 | `orderflow_order` |
| `payment-service` | Payment processing | 8085 | `orderflow_payment` |
| `notification-service` | Event-driven notification dispatch | 8086 | `orderflow_notification` |

`common-library` isn't in this table — it's a plain jar dependency (no port, no `@SpringBootApplication`), consumed by the six business services only. See below.

## Common Library

`common-library` is a shared Maven module — not a runnable service — that eliminates copy-pasted cross-cutting code across the six business services. It's consumed by `identity-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`, and `notification-service`. It is **not** consumed by `config-server` or `discovery-server` (no custom REST surface to standardize) or by `api-gateway` (it's a reactive/WebFlux stack; it now has its own `GlobalFilter` for correlation IDs and its own fallback response type instead — see [API Gateway](#api-gateway)).

Everything in it wires in automatically the moment a service adds the dependency, via Spring Boot 3's `AutoConfiguration.imports` mechanism (`common-library/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) — no `@ComponentScan` changes needed in any consuming service.

| Package | Provides |
|---|---|
| `com.orderflow.common.response` | `ApiResponse<T>` — the one response envelope every endpoint returns (success flag, data, message, errors, correlationId, timestamp); `ApiError` for individual field/business errors |
| `com.orderflow.common.exception` | `BusinessException` hierarchy (`ResourceNotFoundException` → 404, `ConflictException` → 409, `UnauthorizedException` → 401, `ValidationException` → 400) + `GlobalExceptionHandler` (validation/parsing/generic-error mapping) + `SecurityExceptionAutoConfiguration` (`AccessDeniedException` → 403, `AuthenticationException` → 401 — split into its own gated autoconfiguration so services without Spring Security never load a class that doesn't exist for them) |
| `com.orderflow.common.correlation` | `CorrelationIdFilter` — reads/generates `X-Correlation-Id`, stores it in MDC for the request lifetime, echoes it on the response; `FeignCorrelationIdInterceptor` — propagates that ID onto outgoing OpenFeign calls so a saga spanning multiple services shares one traceable ID |
| `com.orderflow.common.entity` | `BaseEntity` — `@MappedSuperclass` with `createdAt`/`updatedAt` (via Spring Data JPA auditing) and `@Version` for optimistic locking |
| `com.orderflow.common.security` | **New in Product Service phase.** `JwtService` (issue + verify), `JwtAuthenticationFilter` (stateless, claims-only, no DB lookup), `JwtAuthenticationEntryPoint` (401s in the `ApiResponse` shape), `JwtProperties` — shared by every service that validates a JWT `identity-service` issued, not just `identity-service` itself. See [Product Service](#product-service) for why this moved out of `identity-service`. |

**Conditional activation, not forced dependencies**: `spring-data-jpa`, `feign-core`, `spring-security-core`, `spring-security-web`, and `jjwt-api` are declared `<optional>true</optional>` in `common-library`'s own POM, and the corresponding autoconfiguration classes are conditionally gated. The Feign interceptor only activates if the consuming service already depends on `spring-cloud-starter-openfeign` (`@ConditionalOnClass`). JPA auditing is gated more strictly — on an actual `EntityManagerFactory` **bean** existing (`@ConditionalOnBean`, not just the JPA classes being on the classpath) — because a service can carry `spring-boot-starter-data-jpa` per the intended tech stack without yet having a real datasource or any entities; gating on the class alone was tried first and failed hard ("JPA metamodel must not be empty") the first time a service was actually booted. JWT validation activates only once a service has *both* `jjwt-api` and Spring Security on its own classpath (`identity-service`, `product-service` today). `common-library` never forces a technology onto a service that doesn't already use it, and never assumes a dependency being present means it's actually wired up.

**Deliberately deferred**: `createdBy`/`updatedBy` audit fields (needs an `AuditorAware` backed by the authenticated principal — the JWT security context now exists, but wiring a generic `AuditorAware` that works across every future service's own context is its own piece of design work, not a byproduct of Identity/Product Service existing) and Kafka correlation-ID propagation (deferred to the Kafka integration phase).

Covered by 20 unit tests (`ApiResponseTest`, `GlobalExceptionHandlerTest`, `SecurityExceptionAutoConfigurationTest`, `CorrelationIdFilterTest`, `JwtServiceTest`) — run them with `mvn -pl common-library test`.

## Config Server

`config-server` (port `8888`) centralizes configuration for every other service using Spring Cloud Config's **native** (filesystem) backend — it serves YAML files bundled in its own `config-server/src/main/resources/config-repo/` directory, one per `spring.application.name` (`identity-service.yml`, `product-service.yml`, ...), plus a shared `application.yml` applied to every client. `server.port` for all 8 other services now lives there instead of in each service's local `application.yml` — the concrete, verifiable proof that centralization is actually wired up, not just declared.

Every other service imports it via `spring.config.import=optional:configserver:http://localhost:8888` in its local `application.yml` — the `optional:` prefix means a service still boots on its own if Config Server isn't running, preserving the "any service runs standalone" property from Milestone 1. `config-server` itself is the one module that doesn't (and can't) import from itself.

**It's protected by HTTP Basic Auth** (everything except `/actuator/health` and `/actuator/info`) — credentials via `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` env vars, with dev-only defaults (`config-client` / `config-client-secret`) baked in for local use. Full design rationale, a sequence diagram, the API shape, and interview discussion points (native vs. git backend, why `optional:`, why discovery-based lookup isn't used yet) are in [`docs/config-server.md`](docs/config-server.md).

Verify it directly once running:
```bash
curl http://localhost:8888/actuator/health                                          # no auth needed
curl -u config-client:config-client-secret http://localhost:8888/identity-service/default
```
Covered by 5 tests (`ConfigServerApplicationTests`, `ConfigServerIntegrationTest` — auth enforcement + real native-backend property resolution) — run with `mvn -pl config-server test`.

## Discovery Server

`discovery-server` (port `8761`) is a standalone Netflix Eureka server — the service registry every other service (except `config-server`, which has no Eureka client dependency at all) now registers with on boot, instead of silently relying on Spring Cloud's undocumented `localhost:8761` default the way earlier milestones did.

`eureka.client.service-url.defaultZone` is identical across all 7 registering services, so — like `management.endpoints.web.exposure.include` before it — it lives once in `config-repo/application.yml` (the shared file), not duplicated per service. Eureka's HTTP client has no separate username/password property, so credentials are embedded directly in that URL: `http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@host:port/eureka/`. `discovery-server` receives this shared property too via Config Server, but it's inert there — its own `config-repo/discovery-server.yml` sets `register-with-eureka`/`fetch-registry` to `false`, so no defaultZone is ever used.

It's a standalone (non-clustered), self-preservation-disabled instance — the right shape for local/dev, with the production trade-offs (peer-aware clustering, re-enabling self-preservation) documented in [`docs/discovery-server.md`](docs/discovery-server.md) alongside a sequence diagram, the Eureka REST API surface, and interview notes (AP vs. CP registries, why credentials live in the URL, `prefer-ip-address`).

Like Config Server, it's protected by HTTP Basic Auth (`/actuator/health`/`/actuator/info` open, everything else — including the dashboard and registry API — authenticated) via `EUREKA_USERNAME`/`EUREKA_PASSWORD` env vars (dev-only defaults `eureka-client`/`eureka-client-secret`). Unlike Config Server, Discovery Server *can* fetch its own config, so those credentials are centralized in `config-repo/discovery-server.yml` rather than duplicated locally.

Verify it directly once running:
```bash
curl http://localhost:8761/actuator/health                                                      # no auth needed
curl -u eureka-client:eureka-client-secret -H "Accept: application/json" http://localhost:8761/eureka/apps
```
Or visit `http://localhost:8761/` in a browser (it'll prompt for the same credentials) to see the dashboard.

Covered by 4 tests (`DiscoveryServerApplicationTests`, `DiscoveryServerSecurityIntegrationTest` — health open, registry endpoint requires auth) — run with `mvn -pl discovery-server test`. The test suite disables the Config Server import (`spring.cloud.config.enabled: false` in `src/test/resources/application.yml`) so it stays hermetic and doesn't depend on `config-server` actually running.

## API Gateway

`api-gateway` (port `8080`) is a Spring Cloud Gateway (WebFlux/reactive) instance — the single public entry point that routes `/api/{service}/**` traffic to the right business service, resolved through `discovery-server` by logical name rather than a hardcoded host:port.

Routes live centrally in `config-repo/api-gateway.yml`, one per business service, each shaped the same way: a `Path` predicate, a `StripPrefix=2` filter (so `/api/identity/whoami` reaches `identity-service` as `/whoami`), and a `uri: lb://{SERVICE-NAME}` — the `lb://` scheme is what makes routing go through Eureka-backed client-side load balancing instead of a fixed address.

| External path | Routed to |
|---|---|
| `/api/identity/**` | `identity-service` |
| `/api/products/**` | `product-service` |
| `/api/inventory/**` | `inventory-service` |
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |
| `/api/notifications/**` | `notification-service` |

Every route also carries a Resilience4j `CircuitBreaker` filter (`spring-cloud-starter-circuitbreaker-reactor-resilience4j` — `resilience4j-spring-boot3` alone only supplies the actuator/property-binding side, not the `ReactiveCircuitBreakerFactory` Gateway's filter actually needs). On a downstream failure or timeout, instead of the caller getting a raw connection error, the circuit breaker forwards to `FallbackController` (`com.orderflow.apigateway.fallback`), which returns a `503` with a small JSON envelope (`success`, `message`, `correlationId`, `timestamp`) — a locally-defined shape, not common-library's `ApiResponse`, since api-gateway is reactive/WebFlux and can't take a servlet-stack dependency.

`CorrelationIdGlobalFilter` (`com.orderflow.apigateway.filter`) is the edge equivalent of common-library's servlet `CorrelationIdFilter`: it reads `X-Correlation-Id` off the incoming request (generating one if absent), mutates the request so the id reaches whichever service it's routed to, and echoes it on the response — including on fallback responses, so a failure is just as traceable as a success. Full design rationale, a sequence diagram, and interview discussion points (why a separate resilience dependency is needed, why the fallback response isn't `common-library`'s `ApiResponse`, why MDC-based logging is only partially replicated here) are in [`docs/api-gateway.md`](docs/api-gateway.md).

Verify it directly once running (alongside `config-server`, `discovery-server`, and `identity-service` — the one business service with real endpoints behind it so far; routing to any of the other 5 still lands on the `503` fallback):
```bash
curl -i -X POST http://localhost:8080/api/identity/auth/register -H "Content-Type: application/json" \
  -d '{"email":"jane@orderflow.dev","password":"password123","firstName":"Jane","lastName":"Doe"}'
curl -i -H "X-Correlation-Id: my-trace-id" http://localhost:8080/api/products/anything   # no product-service yet -> fallback, id echoed back unchanged
```

Covered by 7 tests (`ApiGatewayApplicationTests`; `CorrelationIdGlobalFilterTest` — id generation and propagation, pure unit test against a mocked filter chain; `FallbackControllerTest` — envelope shape and correlation-id echo, `WebTestClient` bound directly to the controller; `CircuitBreakerFallbackIntegrationTest` — a real request through the full gateway filter chain to a deliberately unreachable downstream, proving routing + resilience + correlation IDs are wired together end-to-end, not just declared) — run with `mvn -pl api-gateway test`. `src/test/resources/application.yml` overrides the real routes with one pointed at `http://localhost:1` (a guaranteed connection refusal) and disables Config Server/Eureka, so the suite is hermetic.

## Identity Service

`identity-service` (port `8081`) is the first business service with real functionality and a real database (`orderflow_identity`, Postgres) instead of just a bootstrap skeleton: registration, login, JWT issuance, refresh-token rotation, logout, and RBAC.

- **Auth**: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` — access tokens are short-lived signed JWTs (HS256, 15 min default) validated statelessly (no DB lookup per request); refresh tokens are opaque random values, stored only as a SHA-256 hash, and **rotated** on every use (the old one is revoked the moment a new one is issued).
- **RBAC**: two seeded roles, `CUSTOMER` (default on registration) and `ADMIN`, enforced with `@PreAuthorize("hasRole('ADMIN')")` on `GET /users` — `GET /users/me` is open to any authenticated user. A default admin account (`ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars, dev-only defaults `admin@orderflow.dev` / `ChangeMe123!`) is created idempotently on every boot by `AdminBootstrapRunner`, since a real bcrypt hash can't be hand-written into a Flyway migration the way the two role rows are (`V4__seed_roles.sql`).
- **Schema**: `users`, `roles`, `user_roles`, `refresh_tokens` — 5 Flyway migrations, `spring.jpa.hibernate.ddl-auto: validate` (Flyway owns the schema, Hibernate only checks it matches).

This is also the first service requiring Postgres actually running — see [Prerequisites](#prerequisites) below for a one-line `docker run` to stand one up locally.

Full design rationale, an ER diagram, the API surface, and interview discussion points (why refresh tokens rotate, why hashing them is enough without bcrypt-level slowness, why the security filter chain doesn't need `common-library`'s exception handler for missing/invalid tokens) are in [`docs/identity-service.md`](docs/identity-service.md). **As of Product Service (below), the JWT classes referenced there live in `common-library`, not `identity-service` itself** — see [Common Library](#common-library) and [Product Service](#product-service) for why.

Verify it directly once running:
```bash
curl -X POST http://localhost:8081/auth/register -H "Content-Type: application/json" \
  -d '{"email":"jane@orderflow.dev","password":"password123","firstName":"Jane","lastName":"Doe"}'
curl -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" \
  -d '{"email":"jane@orderflow.dev","password":"password123"}'
curl http://localhost:8081/users/me -H "Authorization: Bearer <accessToken>"
```

Covered by 16 tests: `AuthServiceImplTest` (11 tests, Mockito — register/login/refresh/logout success and failure branches); `AdminBootstrapRunnerTest` (idempotency, missing-role failure); `IdentityServiceApplicationTests` and `AuthControllerIntegrationTest` (full Spring context + real Postgres via Testcontainers — register → login → protected endpoint, duplicate-email conflict, bad-password rejection, the admin-only RBAC boundary, and the full refresh-rotation/logout-revocation flows). (`JwtServiceTest` now lives in `common-library` — see [Common Library](#common-library).) Run with `mvn -pl identity-service test` — requires Docker (Testcontainers starts a real, throwaway Postgres for the duration of the test run; nothing is left behind afterward).

## Product Service

`product-service` (port `8082`) is the second business service with real functionality and a real database (`orderflow_product`): catalog CRUD and paginated search. The more significant thing it does, architecturally, is validate JWTs `identity-service` issues — the first proof that `common-library`'s shared auth beans (see [Common Library](#common-library)) actually decouple two independent services rather than just being an abstraction with one user.

- **Catalog**: `GET /products` (search — `name`, `category`, `activeOnly` filters, paginated), `GET /products/{id}` — both public, no auth required. `POST`/`PUT`/`DELETE /products` — all `ADMIN`-only via `@PreAuthorize`, same RBAC pattern as Identity Service.
- **Soft delete**: `DELETE` sets `active = false` rather than removing the row, so a future order/inventory reference to a discontinued product still resolves by id; search excludes inactive products by default.
- **Search**: `JpaSpecificationExecutor` + composable, null-safe `Specification` filters (`ProductSpecifications`) rather than a derived-query-method per filter combination.
- **Auth**: no login flow, no password encoder, no `AuthenticationManager` — this service only ever verifies a token someone else (`identity-service`) issued, using the exact same `JwtAuthenticationFilter`/`JwtService` beans from `common-library`.

Full design rationale and interview discussion points (why the JWT secret moved to the *shared* `config-repo/application.yml`, why HS256 shared-secret rather than RS256 asymmetric signing, why the integration tests mint their own tokens instead of calling `identity-service`) are in [`docs/product-service.md`](docs/product-service.md).

Verify it directly once running:
```bash
# Public: no auth needed
curl "http://localhost:8082/products?name=widget"

# Admin-only: requires a real access token from identity-service login
curl -X POST http://localhost:8082/products -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A widget","sku":"WID-1","price":9.99,"category":"Tools"}'
```

Covered by 12 tests: `ProductServiceImplTest` (10 tests, Mockito — create/update/delete/search/getById, SKU-conflict and not-found handling); `ProductServiceApplicationTests` and `ProductControllerIntegrationTest` (full Spring context + real Postgres via Testcontainers — public read without auth, `401` with no token, `403` for a `CUSTOMER`-role token, full create/update/delete for an `ADMIN`-role token, SKU conflict, case-insensitive search). The integration test mints its own JWTs by autowiring the shared `JwtService` directly — `product-service` never runs or calls `identity-service` in this suite, which is the point. Run with `mvn -pl product-service test` — requires Docker.

## Prerequisites

| Tool | Version | Required for |
|---|---|---|
| JDK | 21+ (this repo was built/verified against JDK 23, targeting `--release 21`) | Everything |
| Maven | 3.8+ (verified against 3.9.15) | Building/running |
| Docker | Latest | Running `identity-service`/`product-service` (both need a real Postgres) and their `mvn test` (Testcontainers spins up a throwaway one per suite) — Redis/Kafka containers are *not needed yet, added in a later phase*. Full multi-service `docker-compose.yml` is also a later phase (item 17) — deliberately not introduced early as a placeholder |
| Git | Any recent version | Cloning, version control |

Check your local versions:

```bash
java -version
mvn -v
docker --version
```

The six business services each need a real Postgres database, and order-service and notification-service also need Kafka; product-service uses Redis for its cache. One-time local setup — one Postgres container with one database per service, plus Redis and Kafka:

```bash
docker run -d --name orderflow-postgres -p 5432:5432 \
  -e POSTGRES_USER=orderflow -e POSTGRES_PASSWORD=orderflow -e POSTGRES_DB=orderflow_identity \
  postgres:16-alpine
for db in orderflow_product orderflow_inventory orderflow_order orderflow_payment orderflow_notification; do
  docker exec orderflow-postgres psql -U orderflow -d orderflow_identity -c "CREATE DATABASE $db;"
done

docker run -d --name orderflow-redis -p 6379:6379 redis:7-alpine

docker run -d --name orderflow-kafka -p 9092:9092 \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  apache/kafka:3.7.0
```
`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`, `REDIS_HOST`/`REDIS_PORT` and `KAFKA_BOOTSTRAP_SERVERS` override the defaults. Flyway creates each service's schema automatically the first time it boots.

If port 5432 is already in use on your machine (another project's Postgres, or a native install), publish the container on another host port (e.g. `-p 5440:5432`) and set `DB_PORT` accordingly. The live run documented in `docs/phase-4.md` was done that way.

## Getting Started

Clone the repo and build the whole reactor from the root:

```bash
git clone <repo-url> orderflow
cd orderflow

# Fast structural/dependency check — no compilation
mvn -q validate

# Full build: compiles and installs every module into your local .m2
mvn clean install
```

If this is your first build, Maven will download the Spring Boot / Spring Cloud BOMs and all transitive dependencies — expect it to take a few minutes the first time and be near-instant afterward (cached in `~/.m2`).

## Running the Platform

### Right now (all business services through phase 4)

Every business service now has real behaviour and runs against its own Postgres database. `order-service` and `notification-service` also need Kafka, and `product-service` uses Redis for its cache. The infrastructure containers are listed in [Prerequisites](#prerequisites). `api-gateway` routes to all six business services; the gateway itself performs no authentication — each service validates the JWT.

The live end-to-end run described in `docs/phase-4.md` (§4.1) was done with the platform started as follows, which is the order that matters:

1. Infrastructure containers (Postgres, Redis, Kafka).
2. `config-server`, then `discovery-server` (the others read config and register on boot).
3. The six business services (`identity`, `product`, `inventory`, `payment`, `order`, `notification`), optionally with `api-gateway`.

Run `mvn clean package -DskipTests` first, then `java -jar <service>/target/<service>-0.0.1-SNAPSHOT.jar`. A clean build is needed after source changes to avoid stale class files. 8 of the 9 services pull `server.port` from `config-server`; `api-gateway` + the 6 business services register with `discovery-server`; `api-gateway` routes real traffic to `identity-service` and `product-service` and fallback-only traffic to the other 4. **Start `config-server`, then `discovery-server`, then (if you want `identity-service`/`product-service` up) the Postgres container, then whichever services you want:**

```bash
# From the repo root:
mvn -pl config-server -am spring-boot:run
mvn -pl discovery-server -am spring-boot:run
```

`-am` ("also make") builds any modules that one depends on first — with this repo's dependency graph that mainly matters the first time you run a module before `mvn install` has populated your local repo.

Then, in separate terminals, start whichever other services you want (each still boots fine on its own if `config-server`/`discovery-server` aren't running — the config import is `optional:` and Eureka registration just retries quietly in the background; `identity-service` and `product-service` are the exception — both need Postgres actually reachable, since they have real datasources now):

```bash
mvn -pl api-gateway -am spring-boot:run
mvn -pl identity-service -am spring-boot:run
mvn -pl product-service -am spring-boot:run
mvn -pl inventory-service -am spring-boot:run
mvn -pl order-service -am spring-boot:run
mvn -pl payment-service -am spring-boot:run
mvn -pl notification-service -am spring-boot:run
```

This has been live-verified, not just assumed: the full register → login → protected-endpoint → refresh → logout flow against `identity-service` and a real Postgres is exercised by `AuthControllerIntegrationTest` (see [Identity Service](#identity-service)); the equivalent catalog CRUD + RBAC flow against `product-service` is exercised by `ProductControllerIntegrationTest` (see [Product Service](#product-service)), including minting a JWT with the shared `common-library` `JwtService` and having `product-service` accept it with zero coupling to `identity-service`; and `config-server` + `identity-service` were booted together with `curl http://localhost:8081/actuator/health` returning `{"status":"UP"}`.

Redis health override (`management.health.redis.enabled: false`) remains in `config-repo/*.yml` for the services that do not use Redis yet. It is only a temporary workaround for an unreachable Redis and should be removed per service as that service adopts Redis:
- **Actuator health**: the Eureka health indicator was removed everywhere once Discovery Server went live. Services that do not use Redis keep the Redis override above; `product-service` no longer has it.

### Once the platform is fully built (target end state)

The intended startup order — and the order `docker-compose.yml` will encode once it exists — is:

1. **Infrastructure containers**: PostgreSQL (one instance/schema per service, or one container per service), Redis, Kafka + Zookeeper/KRaft.
2. **`config-server`** — every other service pulls its config from here on boot.
3. **`discovery-server`** — every other service registers here.
4. **`api-gateway`** — the single entry point for all external traffic.
5. **Business services** in any order: `identity-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`, `notification-service`.

At that point, all traffic goes through the gateway on `:8080`, and individual service ports are only for direct debugging.

## Verifying a Service Is Up

Every service exposes Spring Boot Actuator. Once a service is running, check its health endpoint:

```bash
curl http://localhost:8081/actuator/health   # identity-service, for example
```

Expected response once the app has started:

```json
{"status":"UP"}
```

Port reference is in the [Module Map](#module-map) above. `discovery-server` also has a browsable dashboard at [http://localhost:8761](http://localhost:8761) once it's running.

Once controllers exist, each business service will additionally expose interactive API docs via springdoc at `/swagger-ui.html`.

## Project Structure

```
orderflow/
├── pom.xml                  parent (dependency & plugin management)
├── common-library/          shared conventions (ApiResponse, exceptions, correlation ID, BaseEntity)
├── config-server/
├── discovery-server/
├── api-gateway/
├── identity-service/
├── product-service/
├── inventory-service/
├── order-service/
├── payment-service/
├── notification-service/
├── docs/                    architecture docs, diagrams, ADRs (added as each module is built)
├── scripts/                 dev/ops scripts (added as needed)
└── infrastructure/          infra-as-code / local infra config (added in the Docker phase)
```

Each **business service** (`identity`, `product`, `inventory`, `order`, `payment`, `notification`) follows the same domain-driven package layout:

```
com.orderflow.<service>
├── config          application/security/cache configuration
├── controller      REST controllers
├── service
│   ├── impl        service implementations
│   └── interfaces  service contracts
├── repository      Spring Data JPA repositories
├── entity          JPA entities
├── dto             request/response DTOs (records where possible)
├── mapper          MapStruct mappers
├── event           domain/integration event payloads
├── kafka           producers & consumers
├── outbox          transactional outbox pattern
├── saga            saga participants/orchestration
├── cache           Redis cache abstractions
├── security        JWT filters, method security
├── audit           audit logging
├── validation      custom validators
├── exception       exceptions + global handler
├── util            stateless helpers
└── common          shared constants/enums local to the service
```

`config-server`, `discovery-server`, and `api-gateway` are infrastructure services, not domain services — they only get a `config` package.

## Engineering Standards

- Clean Architecture, SOLID, DRY, KISS
- Domain-driven package organization (above)
- Repository, DTO, and Service-Layer patterns; constructor injection everywhere
- Java records for DTOs where appropriate
- Global exception handling + a generic API response envelope
- Structured logging, audit logging, correlation IDs across service boundaries
- Optimistic locking, idempotency, retry, dead-letter queues, circuit breakers (Resilience4j)
- Redis caching where it earns its keep
- Outbox pattern and Saga pattern for cross-service consistency
- Never shared databases — every service owns its schema exclusively

## Why This Foundation Looks the Way It Does

**Parent POM imports BOMs instead of extending `spring-boot-starter-parent`.**
`orderflow-parent` is `groupId: com.orderflow`, packaging `pom`, and pulls in `spring-boot-dependencies` and `spring-cloud-dependencies` via `<dependencyManagement><scope>import</scope>`. This keeps the multi-module tree's own identity at the root while still getting Spring's curated, mutually-compatible dependency versions — the pattern real internal enterprise repos use, as opposed to tutorial repos that just extend the Spring Boot parent directly.

**Versions**: Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3 (verified compatible release train), MapStruct 1.6.3, Lombok 1.18.34, JJWT 0.12.6, Resilience4j 2.2.0, springdoc-openapi 2.6.0.

**Annotation processing is centralized.** `maven-compiler-plugin` is configured once, in the parent's `pluginManagement`, with the Lombok → `lombok-mapstruct-binding` → MapStruct processor order required for MapStruct to see Lombok-generated accessors. Because Maven applies `pluginManagement` settings to default-lifecycle-bound plugins automatically, no child module has to repeat this configuration.

**`spring-boot-maven-plugin`** is version-managed centrally but only *declared* (with a `repackage` execution) in the 9 real services — the parent POM is `pom`-packaged and produces no runnable artifact.

**What's intentionally not wired up yet**: `application.yml` in every service currently has only `spring.application.name` and `server.port`. No service registers with Eureka via explicit config, imports from Config Server, or connects to Kafka/Redis yet — several services already carry the corresponding starter dependencies (e.g., `spring-cloud-starter-netflix-eureka-client`, `spring-kafka`) because they're part of the intended tech stack, but the actual wiring (service-url, config import, topic names, connection properties) is deferred to the phase where that piece of infrastructure is implemented and can be verified end-to-end, per the module-by-module build order. Wiring against infrastructure that doesn't exist yet would create configuration nobody could validate.

**Inter-service call ownership**: `order-service` orchestrates the order saga and therefore carries `spring-cloud-starter-openfeign` to call `product-service`, `inventory-service`, and `payment-service`. `inventory-service` and `payment-service` also carry OpenFeign for their own validation calls. `product-service` and `notification-service` are call *targets* / event consumers and don't need outbound Feign clients.
