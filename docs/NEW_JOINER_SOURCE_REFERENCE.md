# Ecommerce Microservices - Complete Implementation Guide

## 1. Purpose and Audience

This document is the complete implementation reference for this repository.

Use it when you need to:

- Understand the architecture and runtime topology.
- Trace request and event flows across all services.
- Operate the stack locally with Docker Compose.
- Debug service, Kafka, DB, UI, and observability issues.
- Onboard quickly as a new engineer.

## 2. Technology Stack

- Java 17
- Maven multi-module project
- Spring Boot `3.3.8`
- Spring Cloud `2023.0.5`
- Spring Cloud Gateway + Eureka
- PostgreSQL 16
- Kafka (Confluent images) + Zookeeper
- Prometheus + Grafana + Loki + Promtail
- Nginx static frontend

## 3. Repository Layout

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
pom.xml
README.md
```

## 4. Runtime Topology

Core platform:

- `discovery-service` (`8761`): Eureka registry.
- `gateway-service` (`8080`): single API entry point.

Domain services:

- `product-service` (`8081`): catalog read/create APIs.
- `inventory-service` (`8082`): stock APIs + saga participant.
- `order-service` (`8083`): order APIs + saga orchestrator logic.
- `notification-service` (`8084`): consumes final lifecycle events.

Infrastructure/UI:

- `postgres` (`5432`)
- `pgadmin` (`5050`)
- `zookeeper` + `kafka` (`9092`)
- `kafka-ui` (`8090`)
- `prometheus` (`9090`)
- `grafana` (`3000`)
- `loki` (`3100`) + `promtail`
- `storefront` (`5173`)

Timezone configuration:

- Docker services set `TZ=Asia/Kolkata`.
- Grafana default timezone set to `Asia/Kolkata`.
- Frontend renders order/notification timestamps in IST.

## 5. Design Patterns Used

### 5.1 API Gateway Pattern

Implemented in `gateway-service` with static routes:

- `/api/products/**` -> `lb://product-service`
- `/api/inventory/**` -> `lb://inventory-service`
- `/api/orders/**` -> `lb://order-service`
- `/api/notifications/**` -> `lb://notification-service`

### 5.2 Service Discovery Pattern

Implemented with Eureka:

- `discovery-service` runs Eureka server.
- Other services register as Eureka clients.

### 5.3 Saga Pattern (Event-Driven)

Order processing is a distributed transaction flow:

1. Order is created as `PENDING`.
2. `order-service` publishes `order-created-events`.
3. `inventory-service` processes and publishes `inventory-events`.
4. `order-service` finalizes as `CONFIRMED` or `CANCELLED`.
5. `order-service` publishes final `order-events`.
6. `notification-service` consumes and persists notification state.

### 5.4 Outbox Pattern

Implemented in:

- `services/order-service/.../outbox/*`
- `services/inventory-service/.../outbox/*`

Behavior:

- Business state and outbox row are written in one DB transaction.
- Scheduled publisher reads `PENDING` rows and publishes to Kafka.
- On publish success: row becomes `PUBLISHED`.
- On failure: retry metadata (`attempts`, `lastError`) is updated.

### 5.5 Resilience Patterns

Gateway-level:

- Route-specific `CircuitBreaker` + `Retry`.
- Global in-memory token bucket rate limiter (`GlobalInMemoryRateLimiterFilter`).
- Fallback endpoint: `/fallback/{serviceId}` returns 503 payload.

Order-service outbound product lookup:

- `@CircuitBreaker`
- `@Retry`
- `@RateLimiter`
- `@Bulkhead`

Implemented in:

- `services/order-service/src/main/java/com/ecommerce/order/service/ProductCatalogClient.java`

## 6. Service-by-Service Implementation

## 6.1 discovery-service

Key files:

- `services/discovery-service/src/main/java/com/ecommerce/discovery/DiscoveryServiceApplication.java`
- `services/discovery-service/src/main/resources/application.yml`

Responsibilities:

- Runs Eureka registry (`@EnableEurekaServer`).
- Exposes health and Prometheus metrics.

## 6.2 gateway-service

Key files:

- `services/gateway-service/src/main/resources/application.yml`
- `services/gateway-service/src/main/java/com/ecommerce/gateway/filter/GlobalInMemoryRateLimiterFilter.java`
- `services/gateway-service/src/main/java/com/ecommerce/gateway/api/GatewayFallbackController.java`
- `services/gateway-service/src/main/java/com/ecommerce/gateway/filter/GatewayRateLimiterProperties.java`

Responsibilities:

- Routes all external API calls to internal services.
- Enforces gateway resilience:
  - circuit breaker
  - retry
  - global rate limiting
- Provides fallback responses for downstream outages.

Rate limiter defaults:

- `capacity: 80`
- `refillTokens: 80`
- `refillDuration: 1s`

## 6.3 product-service

Key files:

- `services/product-service/src/main/java/com/ecommerce/product/api/ProductController.java`
- `services/product-service/src/main/java/com/ecommerce/product/service/ProductService.java`
- `services/product-service/src/main/java/com/ecommerce/product/config/CatalogBootstrap.java`

Endpoints:

- `GET /api/products`
- `GET /api/products/sku/{skuCode}`
- `POST /api/products`

Responsibilities:

- Maintains product catalog.
- Seeds initial catalog data on startup if table is empty.

Data:

- Database: `product_db`
- Main table: `products`

## 6.4 inventory-service

Key files:

- `services/inventory-service/src/main/java/com/ecommerce/inventory/api/InventoryController.java`
- `services/inventory-service/src/main/java/com/ecommerce/inventory/service/InventoryService.java`
- `services/inventory-service/src/main/java/com/ecommerce/inventory/service/OrderEventConsumer.java`
- `services/inventory-service/src/main/java/com/ecommerce/inventory/outbox/*`

Endpoints:

- `GET /api/inventory`
- `GET /api/inventory/{skuCode}`
- `PUT /api/inventory/{skuCode}` (frontend inventory update feature)
- `POST /api/inventory/reserve`

Responsibilities:

- Manages inventory counts.
- Consumes `order-created-events`.
- Produces `inventory-events` via outbox.

Data:

- Database: `inventory_db`
- Tables:
  - `inventory`
  - `event_outbox`

## 6.5 order-service

Key files:

- `services/order-service/src/main/java/com/ecommerce/order/api/OrderController.java`
- `services/order-service/src/main/java/com/ecommerce/order/service/OrderService.java`
- `services/order-service/src/main/java/com/ecommerce/order/service/ProductCatalogClient.java`
- `services/order-service/src/main/java/com/ecommerce/order/config/KafkaTopicConfig.java`
- `services/order-service/src/main/java/com/ecommerce/order/outbox/*`

Endpoints:

- `GET /api/orders`
- `GET /api/orders/{orderNumber}`
- `POST /api/orders`

Responsibilities:

- Validates order request and product availability lookup.
- Creates order in `PENDING`.
- Publishes `OrderCreatedEvent` to outbox.
- Consumes `inventory-events` and finalizes order status.
- Publishes final lifecycle event to `order-events` via outbox.

Resilience in product lookup:

- Handles 404 as business error (`ProductNotFoundException`).
- Handles remote failures/fallback as unavailable (`ProductServiceUnavailableException`).

Data:

- Database: `order_db`
- Tables:
  - `customer_orders`
  - `event_outbox`

## 6.6 notification-service

Key files:

- `services/notification-service/src/main/java/com/ecommerce/notification/service/OrderNotificationConsumer.java`
- `services/notification-service/src/main/java/com/ecommerce/notification/service/NotificationService.java`
- `services/notification-service/src/main/java/com/ecommerce/notification/api/NotificationController.java`

Endpoints:

- `GET /api/notifications?limit={n}`
- `GET /api/notifications/{orderNumber}`

Responsibilities:

- Consumes final `order-events`.
- Persists notification/event status for UI and diagnostics.

Data:

- Datasource currently points to DB `postgres` (not `notification_db`).
- Table: `notification_events`

## 7. Data and Messaging Contracts

## 7.1 PostgreSQL Databases

Created by `infra/postgres/init-db.sql`:

- `product_db`
- `inventory_db`
- `order_db`
- `notification_db`

Current datasource mapping:

- product-service -> `product_db`
- inventory-service -> `inventory_db`
- order-service -> `order_db`
- notification-service -> `postgres`

## 7.2 Kafka Topics

Configured in `order-service`:

- `order-created-events`
- `inventory-events`
- `order-events`

Local defaults:

- Partitions: `1`
- Replicas: `1`

## 7.3 Event Flow Summary

- `OrderCreatedEvent`: order -> inventory
- `InventoryProcessedEvent`: inventory -> order
- `OrderLifecycleEvent`: order -> notification

## 8. Frontend (Storefront) Implementation

Files:

- `ui/storefront/index.html`
- `ui/storefront/app.js`
- `ui/storefront/styles.css`

Capabilities:

- Product catalog rendering.
- Place order.
- Saga status badges (order/inventory/notification).
- Inventory snapshot + inline quantity update.
- Recent orders + notifications views.
- Auto-refresh every 10 seconds.
- IST timestamp formatting (`Asia/Kolkata`).

## 9. Observability and Operations

## 9.1 Prometheus

Config: `infra/prometheus/prometheus.yml`

Scrapes:

- discovery-service
- gateway-service
- product-service
- inventory-service
- order-service
- notification-service

## 9.2 Grafana

Provisioning:

- Datasources: `infra/grafana/provisioning/datasources/datasources.yml`
- Dashboard provider: `infra/grafana/provisioning/dashboards/dashboard-provider.yml`
- Dashboards:
  - `ecommerce-overview.json`
  - `microservices-service-dashboard.json`

## 9.3 Logging with Loki/Promtail

- Services log to `/app/logs/*.log`.
- Docker volumes mount these logs into promtail.
- Promtail pushes logs to Loki using service `job` labels.

Config:

- `infra/promtail/promtail-config.yml`
- `infra/loki/loki-config.yml`

## 10. Local Runbook

## 10.1 Start Everything

```bash
docker compose up -d --build
```

## 10.2 Verify Containers

```bash
docker compose ps -a
powershell -ExecutionPolicy Bypass -File .\scripts\check-stack.ps1
```

## 10.3 UI and Tool URLs

- Storefront: `http://localhost:5173`
- Gateway: `http://localhost:8080`
- Eureka: `http://localhost:8761`
- Kafka UI: `http://localhost:8090`
- pgAdmin: `http://localhost:5050`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## 10.4 API Smoke Tests

```bash
curl http://localhost:8080/api/products
curl http://localhost:8080/api/inventory
curl http://localhost:8080/api/orders
curl "http://localhost:8080/api/notifications?limit=20"
```

Place order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"LAPTOP-ACER-001\",\"quantity\":1,\"customerEmail\":\"user@example.com\"}"
```

## 10.5 Full Saga Verification

Success path:

1. Place order from UI or API.
2. Confirm order appears as `PENDING` then `CONFIRMED`.
3. Confirm inventory count decreases.
4. Confirm notification entry appears for the same order number.
5. Verify Kafka messages in all three topics.

Failure path:

1. Set SKU quantity to `0` from UI inventory update.
2. Place order for that SKU.
3. Confirm order ends as `CANCELLED`.
4. Confirm notification message includes failure reason.

## 11. Test Suite Reference

Run all tests:

```bash
mvn test
```

Service test coverage map:

- Discovery:
  - `DiscoveryServiceApplicationTest`
- Gateway:
  - `GatewayRoutesTest`
- Product:
  - `ProductControllerTest`
  - `ProductServiceTest`
  - `CatalogBootstrapTest`
- Inventory:
  - `InventoryControllerTest`
  - `InventoryServiceTest`
  - `OrderEventConsumerTest`
  - `InventoryBootstrapTest`
  - `OutboxServiceTest`
  - `OutboxPublisherTest`
- Order:
  - `OrderControllerTest`
  - `OrderServiceTest`
  - `KafkaTopicConfigTest`
  - `OutboxServiceTest`
  - `OutboxPublisherTest`
- Notification:
  - `NotificationControllerTest`
  - `NotificationServiceTest`
  - `OrderNotificationConsumerTest`

## 12. Troubleshooting Guide

Image/tag issues:

- If a Kafka image tag is unavailable, use the compose-defined Confluent images:
  - `confluentinc/cp-zookeeper:7.6.1`
  - `confluentinc/cp-kafka:7.6.1`

Docker Hub TLS timeout during build:

- Symptom: `TLS handshake timeout` while pulling Maven or JDK images.
- Action: retry image pull/build; this is usually network/registry latency.

API/UI not reachable:

1. Run `docker compose ps -a`.
2. Use `scripts/check-stack.ps1`.
3. Check gateway and target service logs.
4. Ensure Eureka has instances for all services.

Kafka event missing:

1. Confirm order creation succeeded.
2. Check `event_outbox` rows in order/inventory DBs.
3. Check outbox publisher logs for publish failures.
4. Inspect Kafka topics in Kafka UI.

## 13. Important Implementation Notes

- Notification service currently uses `postgres` DB instead of `notification_db`.
- Outbox publishers poll every `1500ms` by default (`outbox.publisher.fixed-delay-ms`).
- Retry and fallback are optimized for local/dev behavior, not production tuning.
- Gateway global rate limiter is in-memory and instance-local.

## 14. Contributor Checklist for Changes

When changing business flow, update in the same PR:

1. Service logic and tests.
2. Event contracts (if impacted).
3. UI workflow behavior (if user-visible behavior changes).
4. This guide and `README.md`.

This keeps onboarding and operations documentation accurate.
