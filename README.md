# Simplify Money - Digital Gold Platform (Prototype)

This repository contains a small multi-service Java 21 Spring Boot demo that implements a digital gold purchase workflow across three microservices:

- simplify-money-service (orchestrator)
- payment-gateway-service (mock)
- gold-partner-service (mock)

Key features demonstrated:
- Distributed orchestration
- Correlation IDs
- Idempotency using Idempotency-Key header
- Transaction state machine persisted to MongoDB
- Retry with exponential backoff (Spring Retry)
- Structured logging
- Observability with Micrometer Prometheus and Actuator
- OpenAPI (springdoc)
- Docker + Docker Compose

Prerequisites
- Docker & Docker Compose

Run locally
1. Build & start containers

```bash
# from repository root
docker compose up --build
```

2. Services:
- Simplify: http://localhost:8080
- Payment Gateway Mock: http://localhost:8081
- Gold Partner Mock: http://localhost:8082
- Prometheus: http://localhost:9090

Sample cURL

Initiate purchase:

```bash
curl -v -X POST http://localhost:8080/api/v1/gold/purchase \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-123" \
  -d '{"userId":"user-1","amount":100.0,"paymentMethod":"UPI"}'
```

Get portfolio:

```bash
curl http://localhost:8080/api/v1/portfolio/user-1
```

Get transactions:

```bash
curl http://localhost:8080/api/v1/transactions/user-1
```

Notes, assumptions and tradeoffs
- This is a prototype to showcase architecture and patterns. For production, you'd harden security, validation, monitoring, horizontal scaling, distributed locks for idempotency, and use async message brokers for durability.
- Idempotency implemented with a Mongo collection storing Idempotency-Key -> transaction mapping. For heavy loads, use TTLs and a strong uniqueness constraint.
- Gold allocation is synchronous here but can be made async with Kafka/events.

Project structure
- Each service is a maven module under root
- Dockerfiles at module roots

Next steps / improvements
- Add JWT-based auth and RBAC
- Add integration tests using Testcontainers
- Add Helm/K8s manifests
- Add tracing exporter (OTLP) to a collector


