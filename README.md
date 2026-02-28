# Ecommerce Microservices (Spring Boot + Spring Cloud + Kafka + Postgres)

Production-style ecommerce reference project with a full local stack on Docker Compose.

## What Is Included

- Spring Cloud platform:
  - `discovery-service` (Eureka)
  - `gateway-service` (Spring Cloud Gateway)
- Domain microservices:
  - `product-service`
  - `inventory-service`
  - `order-service`
  - `notification-service`
- Event backbone:
  - Kafka + Zookeeper
  - Saga-style order flow
  - Outbox pattern in `order-service` and `inventory-service`
- Data:
  - PostgreSQL + pgAdmin
- Observability:
  - Prometheus + Grafana
  - Loki + Promtail
- Frontend:
  - Nginx-hosted Storefront UI
  - Inventory quantity update from UI
  - Saga workflow visibility
  - IST timestamp rendering (`Asia/Kolkata`)

## Design Patterns Implemented

- API Gateway pattern (`gateway-service`)
- Service Discovery pattern (`discovery-service`)
- Saga orchestration/choreography via Kafka topics
- Outbox pattern for reliable event publication
- Circuit Breaker + Retry on gateway routes
- Global gateway token-bucket rate limiter
- Circuit Breaker + Retry + RateLimiter + Bulkhead on order -> product call

## Service and Port Map

- Storefront: `http://localhost:5173`
- Gateway: `http://localhost:8080`
- Eureka: `http://localhost:8761`
- Product: `http://localhost:8081`
- Inventory: `http://localhost:8082`
- Order: `http://localhost:8083`
- Notification: `http://localhost:8084`
- Kafka UI: `http://localhost:8090`
- pgAdmin: `http://localhost:5050`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (`admin/admin`)

## One-Command Startup

```bash
docker compose up -d --build
```

pgAdmin defaults:

- Email: `admin@ecommerce.com`
- Password: `admin`
- Postgres host: `postgres`
- Postgres port: `5432`
- Postgres user/password: `postgres/postgres`

## Quick Validation

```bash
curl http://localhost:8080/api/products
curl http://localhost:8080/api/inventory
curl http://localhost:8080/api/orders
curl "http://localhost:8080/api/notifications?limit=20"
```

Place an order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"LAPTOP-ACER-001\",\"quantity\":1,\"customerEmail\":\"user@example.com\"}"
```

## Kafka Topics Used

- `order-created-events`
- `inventory-events`
- `order-events`

## Observability Quick Checks

- Prometheus targets: `http://localhost:9090/targets`
- Grafana dashboards: `Ecommerce` folder
- Loki logs are populated through Promtail from service log volumes

Useful PromQL:

- `up{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service"}`
- `sum(rate(http_server_requests_seconds_count{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service"}[1m])) by (job)`

## Troubleshooting

Stack status and health checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-stack.ps1
```

Rebuild app services only:

```bash
docker compose build discovery-service gateway-service product-service inventory-service order-service notification-service
docker compose up -d
```

Reset volumes when Kafka cluster id/storage gets corrupted:

```bash
docker compose down -v
docker compose up -d --build
```

If image pulls fail with TLS handshake timeout, retry pull/build (it is network/registry latency, not project code):

```bash
docker pull maven:3.9.9-eclipse-temurin-17
docker compose up -d --build
```

## Project Structure

```text
services/
  discovery-service/
  gateway-service/
  product-service/
  inventory-service/
  order-service/
  notification-service/
infra/
  postgres/
  prometheus/
  grafana/
  loki/
  promtail/
ui/
  storefront/
scripts/
docker-compose.yml
```

## Detailed Implementation Guide

See:

- `docs/NEW_JOINER_SOURCE_REFERENCE.md`

That document covers class-level architecture, end-to-end flow, outbox/saga internals, resilience behavior, operational runbook, and test reference.

