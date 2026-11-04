# Deployment & CI

This document explains how to run the system locally with Docker Compose and how the automated CI pipeline validates every change.

## 1. Local Deployment (Docker Compose)
1. Ensure Docker Desktop is running with at least **8 GB** memory.
2. From the repository root run:
```bash
docker compose -f docker/docker-compose.yml up --build -d
```
3. Wait for health checks (the compose file uses `condition: service_healthy`).
4. Verify the gateway is up:
```bash
curl -s http://localhost:8080/actuator/health | jq
```
5. The other services are reachable only inside the Docker network; you can curl them via their network alias, e.g.:
```bash
curl http://order-service:8081/actuator/health
```
6. To stop everything and remove persisted volumes:
```bash
docker compose -f docker/docker-compose.yml down -v
```

## 2. CI Pipeline (GitHub Actions)
The workflow lives at **`.github/workflows/ci.yml`** and runs on every push to `main` and on pull‑request updates.
```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_USER: admin
          POSTGRES_PASSWORD: secret
          POSTGRES_MULTIPLE_DATABASES: orders_db,inventory_db,payments_db
        ports: [5432:5432]
        options: >-
          --health-cmd "pg_isready -U admin"
          --health-interval 5s
          --health-timeout 5s
          --health-retries 5
      kafka:
        image: bitnami/kafka:3.5
        env:
          KAFKA_CFG_NODE_ID: 1
          KAFKA_CFG_PROCESS_ROLES: broker,controller
          KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
          KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
          KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
          KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: 'true'
          KAFKA_CFG_NUM_PARTITIONS: 6
        ports: [9092:9092]
        options: >-
          --health-cmd "nc -z localhost 9092"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7-alpine
        ports: [6379:6379]
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 5s
          --health-timeout 3s
          --health-retries 5

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Build and run tests
        run: ./mvnw verify -DskipITs=false
        env:
          POSTGRES_URL: jdbc:postgresql://localhost:5432/orders_db
          KAFKA_BOOTSTRAP_SERVERS: localhost:9092
          REDIS_URL: redis://localhost:6379

      - name: Run k6 smoke test
        if: success()
        run: |
          docker compose -f docker/docker-compose.yml up -d
          sleep 15   # give services time to start
          npm install -g k6
          k6 run load-tests/order-load-test.js --vus 1 --duration 30s
          docker compose -f docker/docker-compose.yml down -v

      - name: Publish Docker images (optional)
        if: github.ref == 'refs/heads/main'
        run: |
          echo "Login to GHCR and push images – omitted for brevity"
```
### What the pipeline does
1. Spins up **Postgres**, **Kafka (KRaft)** and **Redis** as services.
2. Runs **unit** and **integration** tests (Testcontainers will connect to the above services).
3. Starts the full Docker‑Compose stack and executes a **k6 smoke test** to ensure the end‑to‑end flow works.
4. (Optional) pushes Docker images to GitHub Container Registry for the `main` branch.

## 3. Continuous Deployment (optional)
You can extend the workflow with a second job that deploys the compose stack to a remote VM or Kubernetes cluster using `ssh` or `kubectl`. The same health‑check logic applies.

---
*All CI‑related files are version‑controlled so the pipeline runs automatically on every commit.*
