# Architecture Diagram

```mermaid
flowchart LR
    subgraph Client
        C[React / k6]
    end
    subgraph Gateway
        G[API Gateway]
    end
    subgraph Order[Order Service]
        O1[REST API]
        O2[Outbox]
        O3[Saga Orchestrator]
    end
    subgraph Inventory[Inventory Service]
        I1[Stock & Reservation]
        I2[Outbox]
    end
    subgraph Payment[Payment Service]
        P1[Ledger Adapter]
        P2[Outbox]
    end
    subgraph Notification[Notification Service]
        N1[Log / Email stub]
    end
    subgraph Kafka[Kafka (KRaft)]
        K1[inventory.commands]
        K2[inventory.events]
        K3[payment.commands]
        K4[payment.events]
        K5[order.events]
    end
    subgraph Redis[Redis]
        R1[Idempotency Keys]
        R2[Availability Cache]
        R3[Rate Limiter]
    end
    C --> G
    G --> O1
    O1 --> O2
    O2 --> K1
    K1 --> I1
    I1 --> I2
    I2 --> K2
    K2 --> O3
    O3 --> K3
    K3 --> P1
    P1 --> P2
    P2 --> K4
    K4 --> O3
    O3 --> K5
    K5 --> I1
    K5 --> N1
    G -->|Rate limit| R3
    O1 -->|Idempotency| R1
    I1 -->|Cache read| R2
```

The diagram visualises the request flow, outbox‑relay, and auxiliary Redis usage.

