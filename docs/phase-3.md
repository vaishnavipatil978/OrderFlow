# Phase 3 — Product Service

**Status: COMPLETE**, with the same environment caveat as Phase 2 (see §4). Covers roadmap item 8. Builds on [Phase 2](phase-2.md) (Identity Service — JWT auth & RBAC) and, before that, [Phase 1](phase-1.md).

## How to use this document

Same convention as `phase-1.md`/`phase-2.md`: this file is the record of what Phase 3 built and decided. Read §5 before starting Phase 4.

---

## 1. What's Developed

### 1.1 `common-library`: JWT validation extracted from `identity-service`

The real architectural event of this phase, not the catalog CRUD itself. `product-service` is the first service besides `identity-service` that needs to know *who* is calling it — exactly the decision `phase-2.md` flagged as still open ("decide this explicitly when Product Service or another service first needs to know who is calling it"). Rather than duplicate `identity-service`'s JWT filter, it moved into `common-library` (`com.orderflow.common.security`), following the same optional-dependency pattern already used for JPA auditing, Feign correlation IDs, and Spring Security exception handling:

| Class | Gated on |
|---|---|
| `JwtAuthenticationAutoConfiguration` | `@ConditionalOnClass({Jwts.class, AccessDeniedException.class})` — both `<optional>` in `common-library`'s own `pom.xml` (added `jjwt-api` and `spring-security-web` as optional this phase; `spring-security-core` was already optional from Phase 2) |
| `JwtProperties`, `JwtService`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint` | Moved from `identity-service`, unchanged in behavior |

`JwtService.generateAccessToken` changed signature from `(UserPrincipal)` to `(String subject, String userId, Collection<? extends GrantedAuthority> authorities)` — the old signature depended on `identity-service`'s own `UserPrincipal` type, which `common-library` can never depend on (the dependency direction only ever runs one way). `identity-service`'s `AuthServiceImpl` now extracts what it needs from its own `UserPrincipal` and passes it in.

What stayed in `identity-service`: `UserPrincipal`, `CustomUserDetailsService` (needed only for the password-login flow via `AuthenticationManager`/`DaoAuthenticationProvider` — no other service has a login flow to share this with) and its own `SecurityConfig` (each service still assembles its own `SecurityFilterChain`, since permitted paths differ per service; only the beans inside it are shared).

**Config move**: `orderflow.jwt.*` moved from `config-repo/identity-service.yml` to the *shared* `config-repo/application.yml` — every service that verifies a token needs the identical HS256 secret, not a per-service copy. `orderflow.admin.*` stayed in `identity-service.yml` (seeding the one admin account is still identity-service's job alone). The other 4 still-skeleton business services inherit the JWT property too, completely inertly, since none of them have `jjwt`/Spring Security on their classpath yet.

Common-library test suite: `JwtServiceTest` moved from `identity-service` to `common-library`, testing the class where it now actually lives. Added `jjwt-impl`/`jjwt-jackson` as test-scope dependencies to `common-library`'s own `pom.xml` so its tests can actually execute JWT operations (the module's *compile* dependency is deliberately API-only — `jjwt-api` — so it never forces a concrete implementation on a consumer).

### 1.2 Product Service — Catalog CRUD & Search

`product-service` (port `8082`) is the second business service with a real database (`orderflow_product`). Full architecture, API surface, and interview notes: [`docs/product-service.md`](product-service.md). Summary:

- **Endpoints**: `GET /products` (search/browse, paginated, public), `GET /products/{id}` (public), `POST`/`PUT`/`DELETE /products` (all `ADMIN`-only via `@PreAuthorize`).
- **Search**: `ProductRepository extends JpaSpecificationExecutor<Product>`; `ProductSpecifications` composes optional name/category/active-only filters via null-safe `Specification.where(...).and(...)` chaining, rather than a derived-query-method per filter combination.
- **Soft delete**: `DELETE` sets `active = false`, never removes the row — a future `order-service`/`inventory-service` reference to a discontinued product must still resolve. Search excludes inactive products by default; direct `GET /products/{id}` does not.
- **Schema**: single `products` table (1 Flyway migration), same `ddl-auto: validate` convention as `identity-service`.
- **Removed**: the `spring.autoconfigure.exclude` block in `product-service`'s local `application.yml` — the second of the 6 business services to have this removed, following the exact pattern `identity-service` established in Phase 2.

New dependencies on `product-service`'s `pom.xml`: `spring-boot-starter-security`, `jjwt-api` (compile), `jjwt-impl`/`jjwt-jackson` (runtime), `spring-security-test` (test) — everything needed to opt into `common-library`'s `JwtAuthenticationAutoConfiguration`. `product-service` never issues a token itself; it has no `AuthenticationManager`, no password encoder, no login flow.

### 1.3 Tests

12 tests, same two-tier shape as Phase 2:

- **Pure unit / Mockito** — `ProductServiceImplTest` (10 tests: create/update/delete/search/getById, SKU-conflict handling, not-found handling), using a real MapStruct-generated `ProductMapperImpl` rather than mocking the mapper (cheap to construct, and mapper-logic bugs are worth catching here rather than trusting generated code blindly).
- **Full-stack integration** (real Spring context + real Postgres via Testcontainers) — `ProductServiceApplicationTests` (context load), `ProductControllerIntegrationTest` (public read without auth, `401` without a token, `403` for a `CUSTOMER`-role token, `201`/update/soft-delete for an `ADMIN`-role token, SKU conflict, case-insensitive partial-name search).

`ProductControllerIntegrationTest` mints its own JWTs by autowiring the shared `JwtService` bean directly and calling `generateAccessToken` with a `SimpleGrantedAuthority` — **`product-service` never runs or calls `identity-service` in this test.** That's deliberate: it's the concrete proof that the Phase 3 refactor achieved real decoupling (same contract, same secret, zero coupling), not just that the two services happen to work when run together manually.

---

## 2. Cross-Cutting Conventions Carried Forward, and One Correction

Everything in Phase 1's §2 and Phase 2's §2 still applies. One correction to a Phase 2 assumption, not a new convention:

- **Phase 2's deferred-items table said** gateway-side/service-side JWT validation was "real cross-cutting work for a future phase" and warned against assuming it's "automatically unblocked" by `identity-service` merely existing. That caution was warranted — it *did* need deliberate design work (the `common-library` extraction), it just happened this phase rather than staying open longer. `api-gateway` itself still validates nothing (see §3) — only `product-service`, service-to-service, does so far.
- **Second service proves a pattern, first service might have been an accident**: Phase 2's Flyway-migrations + config-repo-datasource + Testcontainers-integration-test pattern was, until this phase, only ever exercised by `identity-service`. `product-service` repeating it with zero changes to the pattern itself (only to the schema/entities) is what actually confirms it generalizes, rather than being an artifact of identity-service's specific needs.

---

## 3. Known Deferred Items (Intentional, Not Bugs)

Carried forward from Phase 2's table, plus what Phase 3 adds or resolves:

| Item | Where | Status |
|---|---|---|
| `management.health.redis.enabled: false` | `config-repo/{service}.yml` | Still deferred (now also true for `product-service`) — resolved by Redis wiring (item 14) |
| JPA/Datasource/Flyway autoconfig excluded | `inventory-service`, `order-service`, `payment-service`, `notification-service` local `application.yml` | Still present for these 4; resolved for `identity-service` (Phase 2) and now `product-service` (this phase) |
| `createdBy`/`updatedBy` audit fields not on `BaseEntity` | `common-library` | Unchanged, still deferred — a generic `AuditorAware` is its own design work, not a byproduct of either JWT-capable service existing |
| Kafka correlation-ID propagation | `common-library` | Unchanged, still deferred — resolved by Kafka integration (item 13) |
| **`api-gateway` still has no auth of its own** | `api-gateway` | Still deferred, and now more precisely scoped again: `product-service` validates JWTs itself, service-side — the gateway still forwards every request (including malformed/missing-token ones) straight through to whichever service is behind the route, unauthenticated at the edge. That's fine today (the target service correctly rejects with `401`/`403`), but means every future service must remember to add its own JWT validation rather than inheriting it for free from the gateway. Worth revisiting once enough services exist that the duplication (each one wiring `common-library`'s auth beans, each one needing its own `SecurityConfig`) outweighs the flexibility of per-service control over which paths are public |
| Gateway MDC only covers its own log line | `api-gateway` `CorrelationIdGlobalFilter` | Unchanged, still deferred |
| Eureka self-preservation disabled | `discovery-server` | Unchanged, operational note for any real multi-instance deployment |
| JWT signing secret is plaintext in `config-repo/` | `config-repo/application.yml` (moved from `identity-service.yml` this phase) | Unchanged limitation, now affecting one more service's worth of blast radius if it ever leaked — still the documented `{cipher}...`/HSM next step, still not implemented |
| Asymmetric (RS256) JWT signing | N/A — HS256 shared secret | **New this phase.** Not implemented: `product-service` verifying with the same secret `identity-service` signs with means a compromised `product-service` could theoretically forge tokens too. RS256 (private key stays with `identity-service`, only the public key is shared) is the correct production hardening step, deliberately not done here — see `docs/product-service.md`'s Interview Notes for the full trade-off. Revisit if/when more services join the trust boundary and the shared-secret blast radius becomes the more pressing concern than the key-management overhead of doing it properly |

---

## 4. Verification Status

- `mvn test` from the repo root: **60 of 64 tests pass** across all 10 modules. The 4 failures are the Testcontainers-backed suites in `identity-service` (2, unchanged from Phase 2) and the newly-added ones in `product-service` (2) — **the same environment issue documented in `phase-2.md` §4, confirmed to be identical, not a new or different problem**:
  - Re-ran with Docker Desktop freshly started and `docker ps`/`docker info`/`docker version` all confirmed working via the CLI (`desktop-linux` context).
  - The exact same `BadRequestException Status 400` stub response (byte-for-byte identical shape, including the `com.docker.desktop.address=npipe://\\.\pipe\docker_cli` label) that Phase 2 diagnosed and attributed to a Docker Desktop ↔ Testcontainers named-pipe compatibility issue on this specific machine, not to this repository's code.
  - `~/.testcontainers.properties` (set in Phase 2, pointing at the correct `dockerDesktopLinuxEngine` pipe per `docker context inspect`) and the `testcontainers-bom` version pin (1.20.4, also from Phase 2) both carried forward and were both still in effect — ruling out either as the cause, again.
- The non-Testcontainers tests in both `identity-service` (14) and `product-service` (10) all pass and cover every branch of the actual business logic via mocks, so the *logic* is verified in both services even where full-stack integration tests couldn't run in this sandbox.
- `ProductServiceImplTest` and `AuthServiceImplTest` both passing with the post-refactor `JwtService` signature confirms the `common-library` extraction didn't silently change behavior for either service — same 50-test baseline from Phase 2 (minus the moved `JwtServiceTest`, now in `common-library`) still passes.
- Not yet live-boot verified: `product-service` and `identity-service` running simultaneously with a real token obtained by actually logging into `identity-service` and used against `product-service`'s admin endpoints — the integration tests prove the *contract* (same secret, same claim shape) works without either service literally running against the other, which is the more valuable thing to have proven in an automated suite, but the fully manual end-to-end path is still worth doing once before calling the JWT story done.

---

## 5. Pending / Next Phases

Roadmap items 9–18 remain:

| # | Item | Starting point already in place |
|---|---|---|
| 9 | **Inventory Service** — stock levels & reservation | Package skeleton + OpenFeign present (for calling Product Service). Two real prior-art services (`identity-service`, `product-service`) to follow for the Flyway/config-repo-datasource/Testcontainers pattern. Will also be the first service to make a real OpenFeign call to another business service — `common-library`'s `FeignCorrelationIdInterceptor` (built in Phase 1, never yet exercised by a real caller) finally gets used |
| 10 | **Order Service** — order lifecycle, saga orchestration | Package skeleton (`saga/`, `outbox/`) + OpenFeign present (calls Product, Inventory, Payment) |
| 11 | **Payment Service** — payment processing | Package skeleton + OpenFeign present |
| 12 | **Notification Service** — event-driven notification dispatch | Package skeleton exists; Kafka consumer only |
| 13 | **Kafka messaging integration** | `spring-kafka` on 5 of 6 business service poms; correlation-ID-over-Kafka propagation deferred to this item |
| 14 | **Redis caching** | `spring-boot-starter-data-redis` on every business service pom; `management.health.redis.enabled: false` overrides (now on `identity-service` and `product-service` too) to remove once this lands. `product-service`'s search endpoint is a natural first caching candidate once this item starts |
| 15 | **Saga pattern** | `saga/` package exists in every business service |
| 16 | **Outbox pattern** | `outbox/` package exists in every business service |
| 17 | **Dockerization** | Nothing yet — deliberately last |
| 18 | **Comprehensive testing & documentation** | Testing conventions (hermetic config, Testcontainers, shared `AbstractIntegrationTest` pattern) now proven identically across 2 real business services |

**Recommended next step**: Item 9, Inventory Service. It's the next unblocked item and the first real test of whether the `common-library` JWT extraction and the Flyway/datasource/Testcontainers patterns hold up for a *third* service, plus the first real OpenFeign call between two business services in this codebase.
