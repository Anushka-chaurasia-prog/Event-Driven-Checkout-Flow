# Build Guide

This guide explains how to compile, containerise and run the **CheckoutFlow** multi‑module project.

## Prerequisites
- **Java 21** (or use the Maven wrapper that ships its own JDK).
- **Docker Desktop** (or Docker Engine) with at least 8 GB memory.
- **git** (to clone the repo).
- **Maven** (optional – the wrapper `./mvnw` is included).

## 1. Compile the code
```bash
# From the repository root
./mvnw clean verify   # runs unit and integration tests via Testcontainers
```
The command builds all modules, runs Flyway migrations against the in‑memory H2 (used for unit tests) and produces JARs under `*/target/*.jar`.

## 2. Build Docker images (multi‑stage)
Each service contains a `Dockerfile` that follows the same pattern:
```Dockerfile
# ---------- builder stage ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY */pom.xml .
COPY common ./common
COPY gateway ./gateway
COPY order-service ./order-service
COPY inventory-service ./inventory-service
COPY payment-service ./payment-service
COPY notification-service ./notification-service
RUN ./mvnw -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/*/target/*-boot.jar .
ENTRYPOINT ["java","-jar","${SERVICE_JAR}" ]
```
The `${SERVICE_JAR}` environment variable is set in `docker-compose.yml` for each service.

### Example: Build a single service manually
```bash
cd order-service
docker build -t checkoutflow-order:latest \
    --build-arg SERVICE_JAR=order-service-1.0.0-SNAPSHOT.jar .
```

## 3. Docker‑Compose stack
Run the full stack (Postgres, Kafka, Redis, all services, observability) with a single command:
```bash
docker compose -f docker/docker-compose.yml up --build -d
```
The compose file pulls the official images for Postgres, Kafka‑KRaft, Redis, Prometheus, Grafana and Zipkin, then builds the local service images.

## 4. Health checks
```bash
# Gateway (exposed on host)
curl -s http://localhost:8080/actuator/health | jq
# Order service internal health (Docker network alias `order`)
curl -s http://localhost:8081/actuator/health | jq
```
All services expose Spring Boot Actuator endpoints.

## 5. Tear‑down
```bash
docker compose -f docker/docker-compose.yml down -v
```
Removes containers, networks and persisted volumes.

---
*The full `docker-compose.yml` lives under `docker/` and the Dockerfiles under each service directory.*
