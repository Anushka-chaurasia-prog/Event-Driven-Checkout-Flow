# High-Level Design (HLD)

## 1. Purpose
This document provides a build‑ready design for the **CheckoutFlow** project, an event‑driven checkout platform. It leverages your existing **Wallet & Ledger Engine** as the payment backend and introduces event‑driven patterns, caching, failure handling, observability, and containerised deployment.

## 2. Problem & Scope
When a customer clicks **Place Order**, three independent actions must happen:
1. **Reserve stock**
2. **Charge money**
3. **Confirm order**

There is no single DB transaction that spans these systems, so we must guarantee:
- **Never oversell** – sold units never exceed stock.
- **Never double‑charge** – each order results in exactly one successful debit.

## 3. Functional Requirements
| ID | Requirement |
|----|--------------|
| FR1 | Create an order (idempotent, returns `202 Accepted` with order id). |
| FR2 | Atomically reserve stock for all items, or none. |
| FR3 | Charge the customer via the Wallet & Ledger Engine. |
| FR4 | On payment success, confirm the order and permanently deduct reserved stock. |
| FR5 | On any failure, compensate: release stock, refund if needed, and cancel/reject the order. |
| FR6 | Query order status & history. |
| FR7 | Allow customer to cancel while still cancellable. |
| FR8 | Automatically release expired reservations. |
| FR9 | Send a notification on confirmation or cancellation (stub). |
| FR10| Admin can create products and set stock. |

## 4. Non‑Functional Requirements (Design Targets)
| Area | Target |
|------|--------|
| Consistency | Strong consistency inside each service, eventual consistency across services. |
| Idempotency | All commands, events, and HTTP creates are repeat‑safe. |
| Availability | Order creation works even if Kafka, inventory, or payment is briefly down (order stays `PENDING`). |
| Resilience | Timeouts, retries with back‑off, circuit breaker on ledger calls, dead‑letter topics. |
| Latency | Create‑order API < 100 ms p95; end‑to‑end confirmation ~1‑2 s locally. |
| Observability | Correlation id across logs, metrics, traces. |
| Security | JWT validated at gateway; user id forwarded; users can only read their own orders. |

## 5. Architecture Diagram
```
                         +------------------------+
   Client (React/k6) --> |  API Gateway           |-- rate limit (Redis)
                         |  JWT check, routing    |
                         +-----------+------------+
                                     | REST
         +---------------------------+------------------------+
         |                           |                        |
+--------v---------+     +-----------v-------+     +----------v--------+
| order-service    |     | inventory-service |     | payment-service   |
| saga orchestrator|     | stock+reservation |     | ledger adapter    |
| DB: orders_db    |     | DB: inventory_db  |     | DB: payments_db   |
+---+----------^---+     +----+----------^---+     +----+----------^---+
    | outbox   | inbox        | outbox    | inbox        | outbox   | inbox
    v          |              v           |              v          |
+---+----------+--------------+-----------+--------------+----------+----+
|                         Apache Kafka (KRaft)                           |
| inventory.commands  inventory.events  payment.commands  payment.events |
| order.events   and a .DLT dead-letter topic for each                    |
+--------------------------------+---------------------------------------+
                                 |
                      +----------v-----------+
                      | notification-service |
                      +----------------------+
```

## 6. Services & Responsibilities
| Service | Owns | Responsibilities |
|---------|------|-------------------|
| api‑gateway | – | JWT validation, routing, rate limiting, correlation‑id generation. |
| order‑service | `orders_db` | Creates orders, runs saga orchestrator, state machine, time‑outs, queries. |
| inventory‑service | `inventory_db` | Products, stock levels, reservations, TTL‑based expiry, cache reads. |
| payment‑service | `payments_db` | Calls Wallet & Ledger Engine, stores payment records, hides ledger details. |
| notification‑service | – | Consumes order events and logs/email stub notifications. |
| Wallet & Ledger Engine | own PostgreSQL | Existing double‑entry ledger (treated as external). |

## 7. Communication Style
- **Client → System**: REST (POST `/orders` returns `202`, client polls GET `/orders/{id}`).
- **Service → Service**: Asynchronous Kafka for saga steps; synchronous REST only for external ledger calls and cached reads.

## 8. Saga Design (Orchestration)
The **order‑service** is the orchestrator. Other services only react to commands and publish events. The orchestrator decides the next command based on the current order state.

### Happy Path
```
Client → POST /orders → order‑service (creates PENDING, outbox ReserveStockCmd)
ReserveStockCmd → inventory‑service → StockReserved → order‑service (AWAITING_PAYMENT, outbox ChargePaymentCmd)
ChargePaymentCmd → payment‑service → PaymentSucceeded → order‑service (CONFIRMED, outbox OrderConfirmed)
OrderConfirmed → inventory‑service (commit stock) & notification‑service (log).
```

### Compensation Paths
- **Out of Stock** → `StockRejected` → order becomes `REJECTED`.
- **Payment Failed** → order `CANCELLING` → `ReleaseStockCmd` → `StockReleased` → order `CANCELLED`.
- **Timeout / Customer Cancel** → same as payment failure.
- **Late Payment Success** → order `CANCELLING`/`CANCELLED` → `RefundPaymentCmd`.

## 9. State Machine (excerpt)
| From | Event | To | Outbox Action |
|------|-------|----|---------------|
| PENDING | StockReserved | AWAITING_PAYMENT | ChargePaymentCmd |
| AWAITING_PAYMENT | PaymentSucceeded | CONFIRMED | OrderConfirmed |
| AWAITING_PAYMENT | PaymentFailed | CANCELLING | ReleaseStockCmd |
| CANCELLING | StockReleased | CANCELLED | OrderCancelled |
| CANCELLING | PaymentSucceeded (late) | unchanged | RefundPaymentCmd |
| *any* | duplicate/stale event | unchanged | none (log) |

## 10. Kafka Topology
| Topic | Key | Partitions | Producer | Consumer | Messages |
|------|-----|------------|----------|----------|----------|
| inventory.commands | orderId | 6 | order | inventory | ReserveStockCmd, ReleaseStockCmd |
| inventory.events | orderId | 6 | inventory | order | StockReserved, StockRejected, StockReleased |
| payment.commands | orderId | 6 | order | payment | ChargePaymentCmd, RefundPaymentCmd |
| payment.events | orderId | 6 | payment | order | PaymentSucceeded, PaymentFailed, PaymentRefunded |
| order.events | orderId | 6 | order | inventory, notification | OrderConfirmed, OrderCancelled, OrderRejected |
| *.DLT | orderId | 1 | – | – | Failed messages after retries |

## 11. Core Patterns
- **Transactional Outbox** – DB write + Kafka publish in same transaction.
- **Idempotent Consumer (Inbox)** – deduplication table.
- **Saga with Compensation** – local transactions + compensating actions.
- **Conditional Atomic Stock Update** – `UPDATE … WHERE available >= qty`.
- **Reservation TTL** – expiry job releases stale reservations.
- **Idempotency Keys (Redis + DB)** – fast path + ultimate guard.
- **Resilience4j** – timeout, retry, circuit‑breaker for ledger calls.
- **Cache‑Aside (Redis)** – availability reads.
- **Deterministic Lock Ordering** – sort product IDs when updating stock.

---
*This HLD is intended to be copy‑and‑paste ready into your project repository under `docs/HLD.md`.*
