# Testing Strategy

This document describes the testing pyramid for **CheckoutFlow** and the concrete tools used.

## 1. Unit Tests (pure JUnit)
- **Scope**: Domain objects, state‑machine transitions, pure utility classes.
- **Framework**: JUnit 5 + AssertJ.
- **Location**: `src/test/java/...` inside each service module.
- **Typical Cases**:
  - `Order.onStockReserved` yields correct status and next command.
  - `Inventory.reserveStock` atomic update logic (mocked `JdbcTemplate`).
  - `PaymentService.charge` builds correct `LedgerClient` request.

## 2. Integration Tests (Spring Boot + Testcontainers)
- **Purpose**: Verify outbox/inbox, Kafka flow, Redis cache, DB transactions.
- **Dependencies**:
  - `org.testcontainers:kafka` (KRaft mode), `postgresql`, `redis`.
  - `@SpringBootTest` with a random port.
- **Key Scenarios**:
  1. **Happy‑path saga** – POST `/orders`, verify DB rows, Kafka events, payment record.
  2. **Oversell test** – 50 concurrent orders for 10 units, assert exactly 10 CONFIRMED.
  3. **Idempotent create** – duplicate `Idempotency-Key` → single order.
  4. **Outbox recovery** – disable outbox relay, create order, enable relay, ensure saga completes.
  5. **Consumer crash** – throw RuntimeException after inbox insert, ensure redelivery.
  6. **Payment failure** – ledger returns 422, order ends CANCELLED, stock released.
  7. **Ledger timeout & retry** – WireMock delays > 2 s, verify retries with same key.
  8. **Late payment success** – order cancelled, then ledger success → Refund.
  9. **Release before reserve** – send `ReleaseStockCmd` first, later `ReserveStockCmd` rejected.
  10. **Chaos** – stop `payment-service` during load, restart, all orders reach terminal state.

## 3. Contract Tests
- Serialize/deserialize `Envelope` JSON using Jackson.
- Verify backward compatibility of schema versions (store sample JSON in `src/test/resources/contract`).

## 4. End‑to‑End (E2E) Tests
- **Tool**: Docker‑Compose + Awaitility.
- **Steps**:
  1. `docker compose up -d`.
  2. Run a script that POSTs an order, polls `/orders/{id}` until terminal, asserts final status.
  3. Validate that outbox rows are cleared and no DLT messages exist.

## 5. Load & Stress Tests (k6)
- **Location**: `load-tests/order-load-test.js`.
- Scenarios: smoke (1 VU), ramp‑up (100 VU over 2 min), spike, soak (30 min).
- Metrics collected: `http_req_duration`, `checks`, `iterations`, custom checks on `order_status`.
- After each run, run the **Invariant Checker** (see below) to ensure data consistency.

## 6. Invariant Checker (post‑test utility)
A small Java program (`InvariantChecker.java` under `test-utils`) that connects to the three databases and verifies:
- No non‑terminal orders after 5 min.
- `available + reserved + sold = initial stock + restocks` for each product.
- Exactly one `SUCCEEDED` payment per CONFIRMED order.
- Net ledger effect is zero for CANCELLED/REJECTED orders.
- No messages in any `.DLT` topic (query Kafka admin API).

## 7. CI Integration
- `mvn verify` runs unit, integration (Testcontainers) and fails fast on invariant violations.
- k6 scripts are executed in the GitHub Actions workflow (see `ci.yml`).

---
*All test sources live under the respective module's `src/test/java` hierarchy.*
