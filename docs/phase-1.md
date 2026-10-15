# Phase 1 — Foundation & Infrastructure

**Status: COMPLETE.** Covers roadmap items 1–6 (Build Order / Roadmap in the root `README.md`). Everything in this document is either committed as working, tested code, or explicitly called out as deferred/pending.

## How to use this document

One document = one phase. This file is the record of everything Phase 1 built, the decisions behind it, and the loose ends left for later on purpose. When Phase 2 (Identity Service) is done, it gets its own `docs/phase-2.md` — this file doesn't get rewritten to describe later work, only corrected if something recorded here turns out to have been wrong. Read the **Pending / Next Phases** section at the bottom before starting new work; it's the map of what roadmap items 7–18 still need.

---

## 1. What's Developed

### 1.1 Maven Multi-Module Structure (roadmap items 1–2)

Root `pom.xml`: `groupId com.orderflow`, `packaging pom`, 10 modules (`common-library`, `config-server`, `discovery-server`, `api-gateway`, 6 business services). Dependency versions centralized via `<dependencyManagement>` importing `spring-boot-dependencies` (3.3.5) and `spring-cloud-dependencies` (2023.0.3) BOMs, plus explicitly pinned versions for libraries the BOMs don't cover (JJWT 0.12.6, Resilience4j 2.2.0, MapStruct 1.6.3, Lombok 1.18.34, springdoc-openapi 2.6.0). Annotation processor order (Lombok → lombok-mapstruct-binding → MapStruct) is configured once in parent `pluginManagement` and inherited by every child. Java 21, enforced via `maven-enforcer-plugin` (`requireJavaVersion [21,)`, `requireMavenVersion [3.8,)`).

Every one of the 6 business services has the full intended package skeleton already created (empty, no classes yet): `controller`, `service/interfaces`, `service/impl`, `repository`, `entity`, `dto`, `mapper`, `event`, `kafka`, `saga`, `outbox`, `security`, `audit`, `cache`, `config`, `exception`, `validation`, `util`, `common`. Nothing is implemented inside them — this is intentionally just the shape, done up front so each service's real build-out (Phase 2 onward) drops code into an already-agreed structure instead of inventing one per service.

### 1.2 `common-library` (roadmap item 3)

Shared Maven module (plain jar, no `@SpringBootApplication`, no port) consumed by all 6 business services. **Not** consumed by `config-server`, `discovery-server` (no custom REST surface to standardize), or `api-gateway` (reactive/WebFlux — see 1.5). Everything wires in automatically via Spring Boot 3's `AutoConfiguration.imports` mechanism, no `@ComponentScan` needed downstream.

| Package | Contents |
|---|---|
| `com.orderflow.common.response` | `ApiResponse<T>` (success flag, data, message, errors, correlationId, timestamp), `ApiError` |
| `com.orderflow.common.exception` | `BusinessException` base + `ResourceNotFoundException`(404) / `ConflictException`(409) / `UnauthorizedException`(401) / `ValidationException`(400); `GlobalExceptionHandler` (`@RestControllerAdvice`) maps these plus Spring's own validation/parsing/access-denied exceptions to `ApiResponse` |
| `com.orderflow.common.correlation` | `CorrelationIdFilter` (servlet `OncePerRequestFilter`: reads/generates `X-Correlation-Id`, MDC for request lifetime, echoes on response); `FeignCorrelationIdInterceptor` (propagates the same id onto outgoing OpenFeign calls); `CorrelationIdAutoConfiguration`, `FeignCorrelationIdAutoConfiguration` |
| `com.orderflow.common.entity` | `BaseEntity` (`@MappedSuperclass`: `createdAt`/`updatedAt` via JPA auditing, `@Version` optimistic locking); `JpaAuditingAutoConfiguration` |

**Conditional activation** — never forces a technology onto a service that doesn't use it:
- `spring-data-jpa`, `feign-core`, `spring-security-core` are `<optional>true</optional>` in the module's own POM.
- `FeignCorrelationIdInterceptor` activates via `@ConditionalOnClass` on `spring-cloud-starter-openfeign` being present.
- `JpaAuditingAutoConfiguration` activates via `@ConditionalOnBean(EntityManagerFactory.class)` — deliberately *not* `@ConditionalOnClass`, because a service can carry `spring-boot-starter-data-jpa` on the classpath (per the intended stack) without a real datasource yet. Gating on the class alone was tried and failed hard ("JPA metamodel must not be empty") the first time a service was actually booted.

**Deliberately deferred**: `createdBy`/`updatedBy` audit fields (needs `AuditorAware` backed by the JWT security context — doesn't exist until Identity Service); Kafka correlation-ID propagation (deferred to the Kafka integration phase, item 13).

Tests: 16, across `ApiResponseTest`, `GlobalExceptionHandlerTest`, `CorrelationIdFilterTest`. `mvn -pl common-library test`.

### 1.3 Config Server (roadmap item 4)

Port `8888`. Spring Cloud Config Server, **native** (filesystem) backend — serves YAML from its own `config-server/src/main/resources/config-repo/`, one file per `spring.application.name` plus a shared `application.yml`. Every other service imports via `spring.config.import=optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:${CONFIG_SERVER_PORT:8888}` — the `optional:` prefix means a service still boots standalone if Config Server isn't running.

Resolution order per client (highest wins): env vars/CLI args → Config Server's `{service}.yml` → Config Server's shared `application.yml` → client's own local `application.yml`. `server.port` for all 8 other services now lives centrally (`config-repo/*.yml`), not locally — the concrete proof centralization is actually wired, not just declared.

Security: HTTP Basic Auth on everything except `/actuator/health` and `/actuator/info` (`SecurityConfig`, `@EnableWebSecurity`). Credentials via `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` env vars, dev-only defaults `config-client`/`config-client-secret` baked in.

Tests: 5, across `ConfigServerApplicationTests`, `ConfigServerIntegrationTest` (auth enforcement + real native-backend property resolution). `mvn -pl config-server test`. Full design writeup: [`docs/config-server.md`](config-server.md).

### 1.4 Discovery Server (roadmap item 5)

Port `8761`. Standalone (non-clustered) Netflix Eureka server. `register-with-eureka: false`, `fetch-registry: false` (doesn't act as its own client), `enable-self-preservation: false` (meaningless with one dev instance of everything — must be reconsidered before any real multi-instance deployment).

`eureka.client.service-url.defaultZone` lives once in the shared `config-repo/application.yml`, identical for every registering client, with Basic Auth credentials embedded directly in the URL (Eureka's HTTP client has no separate username/password property): `http://${EUREKA_USERNAME:eureka-client}:${EUREKA_PASSWORD:eureka-client-secret}@${EUREKA_HOST:localhost}:${EUREKA_PORT:8761}/eureka/`. `prefer-ip-address: true` set once, shared, so registration works uniformly in containerized environments later.

All 7 registering services (`api-gateway` + 6 business services) carry `spring-cloud-starter-netflix-eureka-client` on the classpath and the shared defaultZone config — `discovery-server` itself receives the shared property too but it's inert there (its own config disables register/fetch). `config-server` has no Eureka client dependency at all.

Security: same Basic Auth pattern as Config Server (`/actuator/health`/`/actuator/info` open, dashboard + registry API authenticated). Credentials via `EUREKA_USERNAME`/`EUREKA_PASSWORD`, dev-only defaults `eureka-client`/`eureka-client-secret`, centralized in `config-repo/discovery-server.yml` (Discovery Server, unlike Config Server, *can* fetch its own config).

Tests: 4, across `DiscoveryServerApplicationTests`, `DiscoveryServerSecurityIntegrationTest`. Hermetic — `src/test/resources/application.yml` disables the Config Server import so the suite never depends on `config-server` actually running. `mvn -pl discovery-server test`. Full design writeup: [`docs/discovery-server.md`](discovery-server.md).

### 1.5 API Gateway (roadmap item 6)

Port `8080`. Spring Cloud Gateway, reactive/WebFlux. Routes, resilience config, and everything else centralized in `config-repo/api-gateway.yml`, same pattern as every other service.

**Routing** — 6 routes, one per business service, each `Path=/api/{name}/**` → `StripPrefix=2` → `CircuitBreaker` filter → `uri: lb://{SERVICE-NAME}` (Eureka-resolved, client-side load-balanced, never a hardcoded host:port):

| External path | Routed to |
|---|---|
| `/api/identity/**` | `identity-service` |
| `/api/products/**` | `product-service` |
| `/api/inventory/**` | `inventory-service` |
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |
| `/api/notifications/**` | `notification-service` |

**Resilience** — Resilience4j `CircuitBreaker` per route (`slidingWindowSize: 10`, `minimumNumberOfCalls: 5`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`) + `TimeLimiter` (`timeoutDuration: 4s`). Needed adding `spring-cloud-starter-circuitbreaker-reactor-resilience4j` on top of the `resilience4j-spring-boot3`/`resilience4j-reactor` already on the business-service poms — the Spring Cloud starter is what actually supplies the `ReactiveCircuitBreakerFactory` Gateway's `CircuitBreaker` filter needs; the plain resilience4j starter only gives property binding + actuator health indicators. Any call that throws or times out — independent of whether the circuit itself is open — is forwarded to `FallbackController` (`com.orderflow.apigateway.fallback`), returning `503` + a small local JSON envelope (`GatewayErrorResponse`: `success`, `message`, `correlationId`, `timestamp`). Not `common-library`'s `ApiResponse` — that module isn't a dependency here (servlet-stack assumptions would fight WebFlux/Netty).

**Correlation IDs at the edge** — `CorrelationIdGlobalFilter` (`com.orderflow.apigateway.filter`, `Ordered.HIGHEST_PRECEDENCE`) reads/generates `X-Correlation-Id` before routing, mutates the request so it reaches whichever service is called, and sets it on the response — including fallback responses. Reactive counterpart to `common-library`'s servlet filter, not a shared class (different stack). MDC is used only around the filter's own single synchronous log line — a full Reactor `Context` ↔ MDC bridge for request-scoped correlated logging across the whole reactive chain is deferred (see docs for why).

Security: deliberately **no** Basic Auth — this is the public edge, and end-user auth is JWT-based, arriving with Identity Service (item 7). `/actuator/gateway/routes` currently discloses route topology to anyone who can reach the gateway; acceptable for now, worth revisiting once there's a real perimeter.

Tests: 7 — `ApiGatewayApplicationTests`; `CorrelationIdGlobalFilterTest` (pure unit, mocked filter chain); `FallbackControllerTest` (`WebTestClient` bound directly to the controller); `CircuitBreakerFallbackIntegrationTest` (a real request through the full filter chain to a deliberately unreachable address — proves routing + resilience + correlation IDs are wired together end to end, not just declared individually). Hermetic — `src/test/resources/application.yml` overrides routes to point at `http://localhost:1` and disables Config Server/Eureka. `mvn -pl api-gateway test`. Full design writeup: [`docs/api-gateway.md`](api-gateway.md).

### 1.6 The 6 Business Services — Current State

`identity-service`, `product-service`, `inventory-service`, `order-service`, `payment-service`, `notification-service`. Each has: a dependency-managed `pom.xml` (web/webflux, validation, JPA, postgresql, Flyway, Redis, Kafka [not identity-service], Eureka client, Config client, Resilience4j, Actuator, Micrometer/Prometheus, springdoc-openapi, MapStruct, Lombok — per the intended stack, whether or not it's wired up yet), a bootstrap `@SpringBootApplication` class, the full package skeleton (empty), and a local `application.yml`.

`identity-service` alone additionally carries `spring-boot-starter-security` + JJWT (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) + `spring-security-test`, since it owns auth. `inventory-service`, `order-service`, `payment-service` carry `spring-cloud-starter-openfeign` (order-service orchestrates the saga and calls the other two; inventory/payment use it for their own validation calls). `product-service` and `notification-service` are call targets/event consumers only, no outbound Feign clients needed.

**No business logic, controllers, entities, repositories, or Flyway migrations exist in any of the 6 yet.** Local `application.yml` per service currently has only: `spring.application.name`, the Config Server import, and a temporary `spring.autoconfigure.exclude` for `DataSourceAutoConfiguration` / `DataSourceTransactionManagerAutoConfiguration` / `HibernateJpaAutoConfiguration` / `FlywayAutoConfiguration` (JPA/Flyway are on the classpath per the intended stack but have no real datasource yet — Spring Boot doesn't wait politely for one, it crashes at startup without this exclusion). `server.port` and Eureka connection details are centralized per-service in `config-repo/{service}.yml`; `management.health.redis.enabled: false` also lives there per service (Redis isn't wired yet — see Known Deferred Items). No tests exist in any of the 6 yet.

---

## 2. Cross-Cutting Conventions Established in Phase 1

These are the decisions every later phase should follow rather than re-litigate:

- **Config precedence**: env vars > Config Server per-service file > Config Server shared file > local `application.yml`. A new config value goes in `config-repo/{service}.yml` unless it's truly identical across every service, in which case it goes in `config-repo/application.yml`.
- **Infra service security pattern**: Basic Auth via `spring.security.user.name/password`, sourced from `{SERVICE}_USERNAME`/`{SERVICE}_PASSWORD` env vars with dev-only in-code defaults, `/actuator/health` + `/actuator/info` always open. Established by Config Server and Discovery Server; deliberately *not* applied to `api-gateway` (see 1.5) or, going forward, to the business services (they'll get real JWT auth instead of Basic Auth once Identity Service exists).
- **Hermetic test convention**: any module whose production config pulls from Config Server/Eureka gets a `src/test/resources/application.yml` that disables `spring.cloud.config.enabled` and Eureka registration, so `mvn test` never depends on infrastructure actually running. Established by `discovery-server`, followed by `api-gateway`; business services should do the same once their tests exist.
- **Correlation ID contract**: header name `X-Correlation-Id`, MDC key `correlationId`, generate-if-absent-else-echo-unchanged. One implementation for the servlet stack (`common-library`), one for the reactive stack (`api-gateway`) — deliberately not shared, since forcing a shared implementation across two different web stacks would mean compromising one or dragging in the wrong stack's dependencies.
- **Deferred-work logging convention**: every deliberate gap (an exclusion, a disabled health indicator, a skipped feature) is called out in a code comment *and* in the relevant `docs/*.md`/README, explicitly naming which roadmap item removes it — exactly so these don't get forgotten. Follow this convention going forward: grep-able comments plus a written trail, not just tribal knowledge.
- **One doc per phase** (this document's own existence): `docs/phase-N.md` records what a phase built and decided, not what's changed since — that's what `docs/{module}.md` and `git log` are for.

---

## 3. Known Deferred Items (Intentional, Not Bugs)

Carried forward from Phase 1 into whichever future phase actually resolves them:

| Item | Where | Resolved by |
|---|---|---|
| `management.health.redis.enabled: false` per business service | `config-repo/{service}.yml` | Redis wiring (item 14) |
| JPA/Datasource/Flyway autoconfig excluded | Each business service's local `application.yml` | That service's own DB-wiring sub-phase (items 7–12) |
| `createdBy`/`updatedBy` audit fields not on `BaseEntity` | `common-library` | JWT security context existing (item 7, Identity Service) |
| Kafka correlation-ID propagation not implemented | `common-library` | Kafka integration (item 13) |
| `api-gateway` has no auth of its own; `/actuator/gateway/routes` is unauthenticated | `api-gateway` | JWT validation at the edge (item 7, once Identity Service issues tokens) |
| MDC in `api-gateway`'s correlation filter only covers its own single log line, not the full reactive chain | `api-gateway` `CorrelationIdGlobalFilter` | A Reactor `Context` ↔ MDC bridge — not currently scheduled against a specific roadmap item; revisit if/when gateway-side structured logging across the whole request becomes a real need |
| Eureka self-preservation disabled | `discovery-server` `config-repo/discovery-server.yml` | Any real multi-instance deployment (not a numbered roadmap item — an operational note for later) |

---

## 4. Verification Status

- `mvn test` from the repo root builds and tests all 10 modules successfully. Total: 32 tests (`common-library` 16, `config-server` 5, `discovery-server` 4, `api-gateway` 7, business services 0 — no code to test yet).
- Live-boot verified (not just unit-tested): `config-server` + `identity-service` booted together, `identity-service`'s `/actuator/health` confirmed `UP` while pulling its port from Config Server. This is what surfaced the JPA/Flyway auto-configure and Actuator health-indicator issues documented in section 3 and in `docs/config-server.md`'s Operational Note.
- Not yet live-boot verified together: the full 9-service stack running simultaneously (`config-server` + `discovery-server` + `api-gateway` + all 6 business services) — reasonable to do once at least one business service has a real endpoint to route to, so the gateway's happy path (not just its fallback path) can be observed end to end.

---

## 5. Pending / Next Phases

Roadmap items 7–18, each still to go through Design → Implementation → Review → Testing. This is the map for `docs/phase-2.md` onward — one phase document per item (or per logical group of items) as they're completed.

| # | Item | Starting point already in place |
|---|---|---|
| 7 | **Identity Service** — registration/login, JWT issuance, RBAC | `spring-boot-starter-security` + JJWT already in `identity-service/pom.xml`; package skeleton (`security/`, `entity/`, `dto/`, etc.) exists; `spring-security-test` already a test dependency |
| 8 | **Product Service** — catalog & search | Package skeleton exists; Redis dependency already present for future search/caching |
| 9 | **Inventory Service** — stock levels & reservation | Package skeleton + OpenFeign already present (for calling Product Service) |
| 10 | **Order Service** — order lifecycle, saga orchestration | Package skeleton (including `saga/`, `outbox/`) + OpenFeign already present (calls Product, Inventory, Payment) |
| 11 | **Payment Service** — payment processing | Package skeleton + OpenFeign already present |
| 12 | **Notification Service** — event-driven notification dispatch | Package skeleton exists; this service is a Kafka *consumer* only, no outbound Feign needed |
| 13 | **Kafka messaging integration** | `spring-kafka` already on 5 of 6 business service poms (not `identity-service`); `common-library`'s correlation-ID-over-Kafka propagation explicitly deferred to this item |
| 14 | **Redis caching** | `spring-boot-starter-data-redis` already on every business service pom; `management.health.redis.enabled: false` overrides to remove once this lands |
| 15 | **Saga pattern** | `saga/` package exists in every business service; likely built alongside/after Order Service (item 10) since Order Service is the orchestrator |
| 16 | **Outbox pattern** | `outbox/` package exists in every business service |
| 17 | **Dockerization** (`Dockerfile` per service + root `docker-compose.yml`) | Nothing yet — deliberately last, built against real working services rather than as a placeholder |
| 18 | **Comprehensive testing & documentation** | Testing conventions already established in Phase 1 (hermetic config, per-module `mvn -pl {module} test`); this item is the final pass across everything, not a from-scratch effort |

**Recommended next step**: Item 7, Identity Service. It's the only unblocked item with no upstream dependency on another unbuilt service, and every other business service's auth story (RBAC, JWT validation at the gateway) depends on it existing first.
