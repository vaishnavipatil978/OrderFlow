# Phase 4 — Business Services Completion (Roadmap items 9–16)

**Status: CODE COMPLETE; LIVE-VERIFIED end to end (18/18 scenario checks). Testcontainers integration suites are written but were NOT executed in this environment — see §4.**

Covers roadmap items 9 (Inventory), 10 (Order), 11 (Payment), 12 (Notification), 13 (Kafka messaging), 14 (Redis caching), 15 (saga pattern), 16 (outbox pattern). Builds on [Phase 1](phase-1.md), [Phase 2](phase-2.md) and [Phase 3](phase-3.md).

Per-service design docs: [`inventory-service.md`](inventory-service.md), [`order-service.md`](order-service.md) (saga, outbox, Kafka), [`payment-service.md`](payment-service.md), [`notification-service.md`](notification-service.md). Product caching is described in [`product-service.md`](product-service.md).

## How to use this document

Same convention as earlier phases. This document covers the whole remaining business-logic scope. Read §5 before starting the next phase.

---

## 1. What's Developed

### 1.1 Inventory Service (item 9)

Stock levels per product with on-hand and reserved quantities, an admin endpoint to set on-hand stock, and the reserve / commit / release operations the saga uses. Reservations are all-or-nothing, idempotent per order, and serialized per product with a row lock. Full model: [`inventory-service.md`](inventory-service.md).

Schema: `inventory_items`, `stock_reservations` (one Flyway migration), with CHECK constraints enforcing `0 <= reserved <= on_hand`.

### 1.2 Payment Service (item 11)

Charge, refund, and lookup for an order. The gateway is simulated and deterministic: a charge is declined only above a 10,000.00 per-transaction limit. Charges are idempotent per order; declines are returned as data (200, status DECLINED) rather than as errors. Full model: [`payment-service.md`](payment-service.md).

Schema: `payments` (one Flyway migration), unique on `order_id`.

### 1.3 Order Service — saga, outbox, Feign (items 10, 15, 16)

The orchestrator. `OrderSaga` runs price → PENDING → reserve → charge → commit → CONFIRMED, with compensations for every completed step on failure. Remote calls run outside any database transaction; local state changes run in short transactions via `TransactionTemplate`.

Events go through a transactional outbox: `OutboxService.enqueue` writes an `outbox_events` row in the same transaction as the order change, and `OutboxPublisher` relays unpublished rows to Kafka every second with `FOR UPDATE SKIP LOCKED`. Full detail: [`order-service.md`](order-service.md).

Feign clients for product, inventory and payment; `FeignConfig` forwards the caller's bearer token so downstream services authorize the customer, not an anonymous service.

Schema: `orders`, `order_items`, `outbox_events` (one Flyway migration).

### 1.4 Notification Service (item 12)

Kafka consumer for `order.events`. Stores one notification per event, deduplicated by `eventId`, and exposes `GET /notifications` scoped to the caller. Sending is simulated with a log line. Failed records retry three times then go to `order.events.DLT`; malformed JSON goes straight to the DLT. Full detail: [`notification-service.md`](notification-service.md).

### 1.5 Kafka messaging (item 13)

- Producer: order-service, `acks=all`, idempotent producer, String key/value (order id key for per-order ordering).
- Consumer: notification-service, `auto-offset-reset=earliest`, `DefaultErrorHandler` with a dead-letter recoverer.
- Topic `order.events` declared by the producer (`KafkaTopicConfig`, 3 partitions).
- Shared broker address in `config-repo/application.yml` (`KAFKA_BOOTSTRAP_SERVERS`).

The correlation-ID-over-Kafka propagation deferred in Phase 1 is still not done; see §3.

### 1.6 Redis caching (item 14)

Product lookups are cached in Redis (`products` cache, 10-minute TTL, key = product id). Updates and deletes evict the entry. Cached values are `Serializable` (`ProductResponse implements Serializable`). Search results are not cached. The `management.health.redis.enabled: false` override was removed from `product-service`, since Redis is now real there.

Other services still carry the Redis health override and do not use Redis yet.

### 1.7 Shared code changes

- `common-library`: new `ServiceUnavailableException` (503), and `SecurityExceptionAutoConfiguration` now has `@Order(HIGHEST_PRECEDENCE)` (see §2, bug 2).
- Each business service's local `application.yml` no longer excludes JPA/Flyway autoconfiguration. All six now have a datasource in their config-repo profile.

---

## 2. Bugs Found by Running the Platform

Building these services and running them live surfaced problems that the earlier phases' code had. Recorded here so they aren't forgotten.

1. **Identity Service could not start** (introduced Phase 2). `UserMapper` was a MapStruct interface whose only methods were `default` methods, so MapStruct generated no implementation and Spring had no bean for it. It is now a plain `@Component`. The Testcontainers suite that would have caught this was never executed in the sandbox, so Phase 2's "19 tests" claim was based on unit tests alone.

2. **Authorization failures returned 500, not 403** (introduced Phase 1). `GlobalExceptionHandler` has a catch-all `@ExceptionHandler(Exception.class)`. Spring picks the first advice that matches, and `SecurityExceptionAutoConfiguration` (which maps `AccessDeniedException` to 403) was not ordered ahead of it. Any `@PreAuthorize` denial therefore became a 500. Fixed with `@Order(HIGHEST_PRECEDENCE)`. Verified live: a customer PUT to an admin endpoint now returns 403.

3. **Stale build outputs.** Running `mvn test` after a source change sometimes produced `ClassFormatError: Invalid method Code length 0` in entity classes, and a fat jar kept an old `common-library` jar. Both were stale build state, not source defects. Clean builds of the affected module fixed each case. Worth doing `clean` before a live run.

4. **Config Server does not resolve placeholders.** It serves `${DB_PORT:5432}` raw and the client resolves it. Overriding a placeholder with a system property on the config server therefore has no effect on clients. The live run passes `--spring.datasource.url=…` on the command line, which takes precedence over config-server values. This is a local-run workaround, not a code change.

---

## 3. Known Gaps (Deliberate, Recorded)

| Item | Where | Resolved by |
|---|---|---|
| Inventory and payment accept any authenticated caller, not only order-service | `inventory-service`, `payment-service` | Service-to-service identity (service account or mTLS) |
| Saga left PENDING by a crash is not swept; its reservation stays RESERVED | `order-service` | A recovery job that finds stale PENDING orders and runs compensations |
| A failed compensation (release/refund) is logged, not retried | `order-service` `OrderSaga` | Retry/reconciliation job (same as above) |
| Notification sending is a log line | `notification-service` | Real email/SMS provider adapter |
| Payment gateway is deterministic and simulated | `payment-service` | Real provider adapter with its own transaction reference |
| Correlation ID is not propagated through Kafka | `common-library` / Kafka headers | Add to outbox relay and listener (deferred since Phase 1) |
| Other services still carry `management.health.redis.enabled: false` | `config-repo/*.yml` | Each service's own Redis adoption |
| `createdBy`/`updatedBy` audit fields | `common-library` | Unchanged from Phase 3 |
| Gateway performs no auth at the edge | `api-gateway` | Unchanged from Phase 3 |
| Multi-instance outbox relay uses `SKIP LOCKED` but duplicates are possible on crash | `order-service` | Accepted; consumers are idempotent |

---

## 4. Verification Status

### 4.1 Live end-to-end run (executed)

Platform run locally: config-server, discovery-server, identity, product, inventory, payment, order and notification services, against real Postgres, Redis and Kafka containers. Scenario script (`e2e.js`, run against the real HTTP APIs):

**18 of 18 checks passed**, covering:

- Customer registration and admin login
- Admin creates a product and sets stock
- A customer order runs the full saga to CONFIRMED (total 50.00), stock committed (on hand 10 → 8, reserved 0), payment CAPTURED
- Order readable by its owner
- Insufficient stock returns 409 and cancels the order
- A declined payment (over the limit) returns 409, and the reservation is released (reserved 0, available 5)
- Unauthenticated order returns 401; customer setting stock returns 403
- Product cache is evicted on update (25 → 30)
- Confirmed and cancelled order events are delivered through Kafka and stored as notifications
- The customer's order list shows all three orders

### 4.2 Unit tests (executed)

Across the changed modules, Mockito and pure unit tests: **70 passing, 0 failing** (common-library 20, identity 14, product 10, inventory 10, payment 6, order 6, notification 4).

### 4.3 Testcontainers integration suites (written, NOT executed)

Written for product, inventory, payment, order and notification, following the same shape as identity's suite. None of them ran in this environment. Docker Desktop was running and `docker ps` worked, but Testcontainers could not reach the Docker engine. Every attempt returned the Docker Desktop CLI proxy's `400` for `/info`, including after pinning `DOCKER_API_VERSION` and upgrading Testcontainers to 1.21.3. This is an environment problem; it has been present since Phase 2.

The live run in §4.1 exercises the same flows against real infrastructure, which is what these suites would prove automatically. They should still be run on a machine where Testcontainers works.

### 4.4 Environment notes for the live run

- The Postgres container is published on host port **5440**. Port 5432 belongs to another project's container, and port 5433 belongs to a native Windows PostgreSQL. The services are started with `--spring.datasource.url=jdbc:postgresql://localhost:5440/<db>`.
- Redis on 6379, Kafka (apache/kafka:3.7.0, single-node KRaft) on 9092.

---

## 5. Pending / Next Phases

Roadmap items 17–18 remain.

| # | Item | Notes |
|---|---|---|
| 17 | **Dockerization** (`Dockerfile` per service + root `docker-compose.yml`) | Nothing yet. The live run in §4.1 is a good starting list of env vars and ports for compose |
| 18 | **Comprehensive testing & documentation** | Run the Testcontainers suites on a working Docker host. Build the recovery job and the service-to-service identity. Add the correlation-ID-over-Kafka propagation |

**Recommended next step**: run the Testcontainers suites on a working Docker host to convert §4.3 into executed results, then item 17. The Testcontainers run is the cheapest way to catch any remaining integration regressions before containerizing.
