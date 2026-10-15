# Phase 2 — Identity Service

**Status: COMPLETE**, with one known environment caveat (see §4). Covers roadmap item 7. Builds on [Phase 1](phase-1.md) (Maven structure, `common-library`, `config-server`, `discovery-server`, `api-gateway`) without changing any of its established conventions, except one bug fix in `common-library` surfaced by actually exercising Spring Security for the first time (§1.1).

## How to use this document

Same convention as [`phase-1.md`](phase-1.md): this file is the record of what Phase 2 built and decided. Read §5 (Pending / Next Phases) before starting Phase 3.

---

## 1. What's Developed

### 1.1 `common-library` fix: gated Spring Security exception handling

Before writing any identity-service code, auditing `common-library` for this phase surfaced a latent bug from Phase 1: `GlobalExceptionHandler.handleAccessDenied` referenced `org.springframework.security.access.AccessDeniedException` directly in an always-registered `@RestControllerAdvice`, but `spring-security-core` is an `<optional>` dependency of `common-library` (see its `pom.xml`) — meaning any of the 5 business services *without* Spring Security on their own classpath would hit `NoClassDefFoundError` the moment they booted a full web context and Spring reflected over the advice's methods. This had never manifested because no business service had booted a web context with a real controller yet.

Fixed by extracting security-exception handling into a new `SecurityExceptionAutoConfiguration` (`com.orderflow.common.exception`), gated with `@ConditionalOnClass(AccessDeniedException.class)` — the same pattern `FeignCorrelationIdAutoConfiguration` already used for its own optional dependency. It also gained a second handler, `AuthenticationException` → `401`, useful to any future service validating JWTs the way `identity-service` does. Registered in `AutoConfiguration.imports` alongside the original. Test coverage moved from `GlobalExceptionHandlerTest` into a new `SecurityExceptionAutoConfigurationTest`; `common-library` went from 16 to 17 tests.

### 1.2 Identity Service — Auth, JWT, RBAC

`identity-service` (port `8081`) is the first business service with real functionality and a real database. Full architecture, API surface, ER diagram, and interview notes: [`docs/identity-service.md`](identity-service.md). Summary:

- **Endpoints**: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET /users/me` (any authenticated user); `GET /users` (`ADMIN` only, `@PreAuthorize`).
- **Tokens**: short-lived signed JWT access tokens (HS256, stateless validation — no DB lookup per request, claims carry everything needed); opaque, SHA-256-hashed, rotating refresh tokens (DB-backed, so logout/revocation is real, not just "let it expire").
- **RBAC**: two Flyway-seeded roles (`CUSTOMER` default, `ADMIN`); a default admin account is created idempotently at every boot by `AdminBootstrapRunner` (`ApplicationRunner`), not via Flyway — a bcrypt hash can't be correctly hand-written into a SQL migration.
- **Schema**: `users`, `roles`, `user_roles`, `refresh_tokens` (5 Flyway migrations). `spring.jpa.hibernate.ddl-auto: validate` — Flyway owns the schema, Hibernate only checks it.
- **Removed**: the `spring.autoconfigure.exclude` block in `identity-service`'s local `application.yml` (JPA/Datasource/Flyway auto-configuration) — the first of the 6 business services to have this removed, since it's the first with a real datasource.

New config in `config-repo/identity-service.yml`: `spring.datasource.*` (env-var driven, dev-only defaults) and `orderflow.jwt.*`/`orderflow.admin.*` (signing secret, token lifetimes, admin credentials — same env-var + dev-fallback pattern as every other credential in the repo, see §3).

### 1.3 Tests

19 tests, in two tiers:

- **Pure unit / Mockito** (no Spring context, no DB) — `JwtServiceTest` (token round-trip, expiry, signature-mismatch rejection), `AuthServiceImplTest` (11 tests: register/login/refresh/logout, every success and failure branch), `AdminBootstrapRunnerTest` (idempotency, missing-seed-role failure).
- **Full-stack integration** (real Spring context + real Postgres via Testcontainers) — `IdentityServiceApplicationTests` (context load), `AuthControllerIntegrationTest` (register → login → protected endpoint; duplicate-email conflict; bad-password rejection; the admin-only RBAC boundary end to end with the seeded admin account; the full refresh-rotation and logout-revocation flows through real HTTP + `MockMvc` + the real Spring Security filter chain).

`AbstractIntegrationTest` is the shared base: one `PostgreSQLContainer` (Testcontainers, JVM-lifetime-shared via the inherited `static` field, not re-created per test class), JDBC URL wired in via `@DynamicPropertySource` rather than a static value in `application.yml` — the container's port isn't known until it's actually started.

---

## 2. Cross-Cutting Conventions Carried Forward, and One Added

Everything in Phase 1's §2 still applies unchanged. One new convention:

- **First real secret in `config-repo/`**: `orderflow.jwt.secret` is the first genuinely high-value secret (a token-signing key, not an internal Basic Auth password) to live in `config-repo/*.yml`. It follows the existing env-var-override + dev-only-fallback pattern rather than introducing a new one, but the stakes are higher than before — see §3 and `docs/identity-service.md`'s Security section for why this specifically needs Spring Cloud Config's encryption support before any real deployment, not just "don't commit real secrets to source control."
- **Idempotent bootstrap runners for data a migration can't produce**: `AdminBootstrapRunner` is the first example of a pattern likely to recur — static/reference data (roles) still goes in Flyway; anything requiring application code to produce correctly (a bcrypt hash at the currently-configured strength) goes in an idempotent `ApplicationRunner`, checked-not-flagged (the existence check *is* the "have I run before" bookkeeping).

---

## 3. Known Deferred Items (Intentional, Not Bugs)

Carried forward from Phase 1's table, plus what Phase 2 adds or resolves:

| Item | Where | Status |
|---|---|---|
| `management.health.redis.enabled: false` | `config-repo/identity-service.yml` | Still deferred — resolved by Redis wiring (item 14) |
| JPA/Datasource/Flyway autoconfig excluded | `identity-service` local `application.yml` | **Resolved this phase** — removed now that a real datasource exists. Still present for the other 5 business services (items 8–12) |
| `createdBy`/`updatedBy` audit fields not on `BaseEntity` | `common-library` | Still deferred. Phase 1 tied this to "the JWT security context existing" — it now does (in `identity-service`), but wiring a generic `AuditorAware` into `common-library` that works across every future service's own security context is its own piece of design work, not a byproduct of this phase. Revisit explicitly, don't assume it's automatically unblocked |
| Kafka correlation-ID propagation | `common-library` | Still deferred — resolved by Kafka integration (item 13) |
| `api-gateway` has no auth of its own | `api-gateway` | Still deferred, and now more precisely scoped: `identity-service` issuing JWTs doesn't by itself give the gateway (or any other service) a way to validate them. Gateway-side JWT validation is real cross-cutting work for a future phase, not something this phase does implicitly |
| Gateway MDC only covers its own log line | `api-gateway` `CorrelationIdGlobalFilter` | Unchanged, still deferred |
| Eureka self-preservation disabled | `discovery-server` | Unchanged, operational note for any real multi-instance deployment |
| **New**: JWT signing secret is plaintext in `config-repo/` | `config-repo/identity-service.yml` | Dev-only, env-var-overridable, same limitation `docs/config-server.md` already flags for the config store as a whole — Spring Cloud Config's `{cipher}...` encryption (backed by a real key/HSM) is the documented next step before any real deployment, not yet implemented |

---

## 4. Verification Status

- `mvn test` from the repo root: **50 of 52 tests pass** across all 10 modules. The 2 failures are both in `identity-service`'s Testcontainers-backed suite (`IdentityServiceApplicationTests`, `AuthControllerIntegrationTest`) and are an **environment issue, not a code defect** — confirmed by:
  - The exact same failure (`BadRequestException Status 400`, a mostly-empty `/info`-shaped response body) occurs identically across two different Testcontainers versions (1.19.8 stock, 1.20.4 explicitly pinned — see the `testcontainers-bom` override added to the root `pom.xml`).
  - `docker info`/`docker version`/`docker ps` all work correctly via the CLI on this machine, using the `desktop-linux` context (`npipe:////./pipe/dockerDesktopLinuxEngine`).
  - `~/.testcontainers.properties` was pointed at that exact same pipe (`docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`), and Testcontainers' `EnvironmentAndSystemPropertyClientProviderStrategy` confirmed picking it up — yet still received the same stub response as the default pipe.
  - This points to a Docker Desktop ↔ Testcontainers/docker-java named-pipe compatibility issue specific to this machine's very recent Docker Desktop build, not to anything in this repository. On a typical CI runner (Linux, real Unix socket) or a different local Docker setup, these 2 tests are expected to pass unmodified.
- The 17 non-Testcontainers tests in `identity-service` (`JwtServiceTest`, `AuthServiceImplTest`, `AdminBootstrapRunnerTest`) all pass and cover every branch of the actual auth business logic (register/login/refresh/logout success and failure paths, token generation/validation/rejection, admin bootstrap idempotency) via mocks — so the *logic* is verified even where the full-stack integration tests couldn't run in this environment.
- Not yet live-boot verified: `identity-service` running continuously alongside `api-gateway` + `discovery-server` + `config-server` all at once with real traffic flowing end to end through the gateway. Worth doing once Product Service (item 8) exists too, so the gateway's happy path can be observed for more than one downstream service.

---

## 5. Pending / Next Phases

Roadmap items 8–18 remain, updated from Phase 1's table:

| # | Item | Starting point already in place |
|---|---|---|
| 8 | **Product Service** — catalog & search | Package skeleton exists; Redis dependency present for future search/caching. `identity-service` is now a concrete reference implementation for the exclusion-removal / Flyway / config-repo-datasource pattern this service will repeat |
| 9 | **Inventory Service** — stock levels & reservation | Package skeleton + OpenFeign already present (for calling Product Service) |
| 10 | **Order Service** — order lifecycle, saga orchestration | Package skeleton (`saga/`, `outbox/`) + OpenFeign already present (calls Product, Inventory, Payment) |
| 11 | **Payment Service** — payment processing | Package skeleton + OpenFeign already present |
| 12 | **Notification Service** — event-driven notification dispatch | Package skeleton exists; Kafka consumer only, no outbound Feign needed |
| 13 | **Kafka messaging integration** | `spring-kafka` on 5 of 6 business service poms; `common-library`'s correlation-ID-over-Kafka propagation explicitly deferred to this item |
| 14 | **Redis caching** | `spring-boot-starter-data-redis` on every business service pom; `management.health.redis.enabled: false` overrides (now present on all 6, including `identity-service`) to remove once this lands |
| 15 | **Saga pattern** | `saga/` package exists in every business service; likely alongside/after Order Service (item 10) |
| 16 | **Outbox pattern** | `outbox/` package exists in every business service |
| 17 | **Dockerization** | Nothing yet — deliberately last. Note: a *standalone* `docker run` for local Postgres now exists (see README Prerequisites) — this is not the full multi-service `docker-compose.yml`, which stays deferred to this item as originally planned |
| 18 | **Comprehensive testing & documentation** | Testing conventions (hermetic config, Testcontainers for real-DB integration tests, per-module `mvn -pl {module} test`) now proven across 5 modules; this item is the final pass, not a from-scratch effort |

**New cross-cutting item worth tracking explicitly** (not in the original numbered roadmap, surfaced by this phase): **gateway-side JWT validation**. `identity-service` can issue tokens; nothing yet validates them anywhere except `identity-service` itself. Before RBAC means anything platform-wide (as opposed to just within identity-service), either `api-gateway` needs to validate JWTs at the edge, or each service needs its own validation filter (likely a shared piece pulled into a security-focused module, possibly `common-library` once more services need it — `identity-service`'s own `JwtService`/`JwtAuthenticationFilter` are not currently shared, by design, since only one service needed them so far). Decide this explicitly when Product Service or another service first needs to know *who* is calling it — don't let it get assumed-solved by identity-service merely existing.

**Recommended next step**: Item 8, Product Service — the next unblocked item, and the first chance to prove the Phase 2 patterns (Flyway migrations, config-repo datasource wiring, real Postgres via Testcontainers) generalize to a second service rather than being identity-service-specific accidents.
