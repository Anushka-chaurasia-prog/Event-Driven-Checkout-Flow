# CheckoutFlow: Event‑Driven Checkout Platform

[![Live Demo](https://img.shields.io/badge/Live_Demo-Vercel-success?style=for-the-badge&logo=vercel)](https://event-driven-checkout-flow-vwbp.vercel.app)
[![GitHub](https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github)](https://github.com/Anushka-chaurasia-prog/Event-Driven-Checkout-Flow)
[![Tech Stack](https://img.shields.io/badge/Java_21-Spring_Boot_3-blue?style=for-the-badge&logo=openjdk)](https://github.com/Anushka-chaurasia-prog/Event-Driven-Checkout-Flow)
[![Messaging](https://img.shields.io/badge/Apache_Kafka-KRaft_Mode-red?style=for-the-badge&logo=apachekafka)](https://github.com/Anushka-chaurasia-prog/Event-Driven-Checkout-Flow)

A multi‑service, event‑driven checkout system built with **Java 21**, **Spring Boot 3**, **Apache Kafka**, **PostgreSQL**, **Redis**, and **Docker Compose**. The project showcases saga orchestration, idempotent processing, transactional outbox, and robust failure handling – perfect for interview demos and resume‑worthy achievements.

🌐 **Live Cloud Interactive Demo:** [https://event-driven-checkout-flow-vwbp.vercel.app](https://event-driven-checkout-flow-vwbp.vercel.app)

## Quick Start
```bash
# Clone the repo (or copy the skeleton you generate)
# git clone <repo-url>
# cd checkoutflow

# Build all modules (requires Maven or the wrapper)
./mvnw verify

# Start the stack (Postgres, Kafka, Redis, services, observability)
docker compose -f docker/docker-compose.yml up -d

# Verify health (Gateway runs on port 8090)
curl http://localhost:8090/actuator/health

# Interactive Web Dashboard
# Open dashboard.html in your browser or visit http://localhost:8090/index.html
```

## Interactive Dashboard
Open [`dashboard.html`](dashboard.html) directly in any browser while the stack is running to:
- Monitor live microservice health
- Execute saga checkout workflows with real-time state visualization (`PENDING` → `AWAITING_PAYMENT` → `CONFIRMED`)
- Test idempotency and out-of-stock compensation flows

## Documentation
- **[Architecture](docs/ARCHITECTURE.md)** – component diagram & communication patterns.
- **[High‑Level Design (HLD)](docs/HLD.md)** – requirements, invariants, service responsibilities.
- **[Low‑Level Design (LLD)](docs/LLD.md)** – repository layout, data model, state machine, outbox/inbox, saga handlers.
- **[Build Guide](docs/BUILD_GUIDE.md)** – Maven, Docker images, env vars.
- **[Testing Strategy](docs/TESTING.md)** – unit, integration, contract, end‑to‑end, load, chaos.
- **[Deployment & CI](docs/DEPLOYMENT.md)** – Docker Compose, GitHub Actions pipeline.
- **[Resume Bullets](docs/RESUME_BULLETS.md)** – measurable statements for your CV.
- **[Interview Q&A](docs/INTERVIEW_QA.md)** – concise answers to common interview questions.

## License
MIT – feel free to adapt for interviews, portfolios, or production experiments.

