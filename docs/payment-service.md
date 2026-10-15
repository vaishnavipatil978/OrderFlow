# Payment Service

## Architecture

`payment-service` (port `8085`, database `orderflow_payment`) charges, refunds, and looks up payments for orders. Only order-service calls it, during the saga.

## Simulated gateway

The payment provider is simulated, and deterministic on purpose: a charge is declined only when its amount exceeds the per-transaction limit, `10000.00`. This keeps both the success path and the compensation path reproducible in tests. A real gateway adapter would replace the decision inside `PaymentServiceImpl.charge`; nothing else in the service would change.

## Payment states

```
charge  -> CAPTURED   (amount within limit)
        -> DECLINED   (amount above limit, decline_reason set)
refund  -> REFUNDED   (only from CAPTURED)
```

## Guarantees

- **Idempotent charge.** One payment row per order (unique `order_id`). A repeated charge returns the original outcome rather than charging again.
- **Declines are data, not errors.** A declined charge returns `200` with status `DECLINED`. The order-service saga branches on that status and compensates. Infrastructure failures surface as non-2xx instead, so the caller can tell "the bank said no" apart from "the bank is down".
- **Idempotent refund.** Refunding an already refunded payment returns it unchanged. Refunding a declined payment is rejected with 409.

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/payments/charge` | authenticated | Charge an order (returns CAPTURED or DECLINED) |
| `POST` | `/payments/{orderId}/refund` | authenticated | Refund a captured payment |
| `GET` | `/payments/order/{orderId}` | authenticated | Payment for an order |

Reached externally through the gateway as `/api/payments/**`.

### Sample curl requests

```bash
curl -X POST http://localhost:8085/payments/charge -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"orderId":"<uuid>","amount":49.99}'
curl -X POST http://localhost:8085/payments/<orderId>/refund -H "Authorization: Bearer <token>"
```

## Known gap

Same as inventory: any authenticated caller can call these endpoints, not only order-service. Service identity is deferred (see `phase-4.md`). A real deployment would also need a durable record of the provider's own transaction reference, which the simulated gateway does not have.

## Tests

- `PaymentServiceImplTest`: capture within limit, decline above limit, idempotent charge, refund rules, unknown order. Mockito.
- `PaymentControllerIntegrationTest`: charge capture/decline over HTTP, refund flow, 404 for unknown order, 401 without a token. Real Postgres via Testcontainers.

## Interview notes

- **Why is a decline a 200 and not a 402?** From the saga's point of view a decline is an expected business outcome that drives compensation, not a transport failure. Encoding it in the body keeps the client from having to distinguish HTTP errors that mean different things.
