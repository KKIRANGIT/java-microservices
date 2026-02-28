# Ecommerce Microservices - Complete Source Code Reference

## 1. Purpose of this document
This document is the source-level onboarding guide for new engineers joining this repository.

Goals:
- Explain how the full system is structured and why.
- Explain each microservice package/class and its role.
- Explain the sync API flow and async Kafka Saga flow.
- Explain the Outbox Pattern implementation.
- Explain how local infrastructure and observability are wired.
- Explain what tests exist and what behavior they protect.

## 2. High-level system architecture
This project is a local production-style ecommerce platform built with:
- Java 17
- Spring Boot 3.3.8
- Spring Cloud 2023.0.5
- Maven multi-module structure
- Kafka (event bus)
- PostgreSQL
- Eureka (service discovery)
- Spring Cloud Gateway (API entry point)
- Prometheus + Grafana + Loki + Promtail (observability)
- Nginx-hosted storefront UI

Service topology:
- `discovery-service` (port `8761`) -> Eureka server.
- `gateway-service` (port `8080`) -> single API entry point.
- `product-service` (port `8081`) -> catalog APIs + product DB.
- `inventory-service` (port `8082`) -> stock APIs + inventory Saga participant.
- `order-service` (port `8083`) -> order APIs + Saga orchestrator/coordinator logic.
- `notification-service` (port `8084`) -> stores and exposes final order lifecycle notifications.

Supporting containers:
- `postgres` (port `5432`)
- `zookeeper`
- `kafka` (port `9092`)
- `kafka-ui` (port `8090`)
- `prometheus` (port `9090`)
- `grafana` (port `3000`)
- `loki` (port `3100`)
- `promtail`
- `storefront` (port `5173`)

## 3. Repository layout
Root layout:
- `services/` -> all Spring Boot microservices.
- `infra/` -> infra configs (prometheus, grafana, loki, promtail, postgres init).
- `ui/` -> storefront assets + Nginx config.
- `scripts/` -> operational scripts (for health/status checks).
- `docker-compose.yml` -> complete local stack orchestration.
- `pom.xml` -> parent Maven POM for all services.

Service modules under `services/`:
- `discovery-service`
- `gateway-service`
- `product-service`
- `inventory-service`
- `order-service`
- `notification-service`

## 4. Build and dependency model
Parent POM: `pom.xml`
- Defines modules and shared versions.
- Imports Spring Boot and Spring Cloud BOMs.
- Configures compiler plugin with `-parameters` support.
- Configures surefire plugin.

Each service has its own `pom.xml` with:
- Core starters (web/webflux/data-jpa/kafka as needed).
- `spring-cloud-starter-netflix-eureka-client` (except discovery server module which is server).
- Actuator + Prometheus registry.
- Test dependencies (`spring-boot-starter-test`).

## 5. Runtime and container orchestration
File: `docker-compose.yml`

Key points:
- Builds all application services from local Dockerfiles.
- Provisions infra dependencies and networks.
- Mounts log volumes used by Promtail.
- Starts Grafana with provisioned datasources/dashboards.
- Starts storefront as static Nginx content.

Startup command:
```bash
docker compose up -d --build
```

## 6. API gateway and service discovery
### 6.1 Discovery service
Files:
- `services/discovery-service/src/main/java/com/ecommerce/discovery/DiscoveryServiceApplication.java`
- `services/discovery-service/src/main/resources/application.yml`

Behavior:
- `@EnableEurekaServer` makes this service the registry.
- `register-with-eureka: false` and `fetch-registry: false` because it is the registry itself.

### 6.2 Gateway service
Files:
- `services/gateway-service/src/main/java/com/ecommerce/gateway/GatewayServiceApplication.java`
- `services/gateway-service/src/main/resources/application.yml`

Routes:
- `/api/products/**` -> `lb://product-service`
- `/api/inventory/**` -> `lb://inventory-service`
- `/api/orders/**` -> `lb://order-service`
- `/api/notifications/**` -> `lb://notification-service`

Gateway configuration notes:
- Uses static route definitions.
- Global CORS allows all origins/methods/headers for local/dev convenience.

## 7. Service-by-service source code reference

---
## 7.1 product-service
Package root: `com.ecommerce.product`

### Purpose
Owns product catalog CRUD/read operations used by UI and order-service.

### Source map
- `ProductServiceApplication` -> Spring Boot entry point.
- `api/ProductController` -> REST endpoints.
- `api/ProductRequest` -> create payload model.
- `api/ProductResponse` -> response model.
- `api/ProductApiExceptionHandler` -> maps `IllegalArgumentException` to 404 JSON error.
- `service/ProductService` -> business logic and model-to-response mapping.
- `model/Product` -> JPA entity for `products`.
- `repository/ProductRepository` -> JPA repository + `findBySkuCode`.
- `config/CatalogBootstrap` -> inserts initial products if table is empty.

### API endpoints
- `GET /api/products` -> list all products.
- `GET /api/products/sku/{skuCode}` -> find one by SKU.
- `POST /api/products` -> create product.

### Data ownership
- DB: `product_db`
- Main table: `products`

---
## 7.2 inventory-service
Package root: `com.ecommerce.inventory`

### Purpose
Provides inventory query/reserve APIs and handles inventory stage in Saga.

### Source map
- `InventoryServiceApplication` -> entry point, enables scheduling.
- `api/InventoryController` -> inventory APIs.
- `api/InventoryResponse` -> response DTO.
- `api/ReserveInventoryRequest` -> reserve request DTO.
- `service/InventoryService` -> inventory logic and Saga handling (`handleOrderCreated`).
- `service/OrderEventConsumer` -> Kafka listener for `order-created-events`.
- `event/OrderCreatedEvent` -> consumed event contract.
- `event/InventoryProcessedEvent` -> produced event contract.
- `model/Inventory` -> JPA entity for stock.
- `repository/InventoryRepository` -> JPA repository + SKU lookup.
- `config/InventoryBootstrap` -> seeds inventory rows if empty.

Outbox package:
- `outbox/OutboxEvent` -> outbox entity (`event_outbox` table).
- `outbox/OutboxStatus` -> `PENDING` or `PUBLISHED`.
- `outbox/OutboxEventRepository` -> query pending events ordered by time.
- `outbox/OutboxService` -> enqueues events transactionally.
- `outbox/OutboxPublisher` -> scheduled relay to Kafka with retry-attempt/error tracking.

### API endpoints
- `GET /api/inventory` -> full inventory snapshot.
- `GET /api/inventory/{skuCode}` -> SKU stock.
- `POST /api/inventory/reserve` -> direct reserve endpoint.

### Event behavior
- Consumes: `order-created-events`.
- Produces (via outbox): `inventory-events`.

### Data ownership
- DB: `inventory_db`
- Tables:
  - `inventory`
  - `event_outbox` (inventory service outbox)

---
## 7.3 order-service
Package root: `com.ecommerce.order`

### Purpose
Creates orders, validates product, orchestrates Saga progression, and publishes final order lifecycle events.

### Source map
- `OrderServiceApplication` -> entry point, enables scheduling.

API package:
- `api/OrderController` -> order APIs.
- `api/OrderRequest` -> create order payload.
- `api/OrderResponse` -> order response payload.
- `api/ApiExceptionHandler` -> maps `OrderPlacementException` to 400 response.

Service and domain:
- `service/OrderService` -> core logic for place/get orders and processing inventory events.
- `service/OrderPlacementException` -> domain-specific runtime exception.
- `model/CustomerOrder` -> JPA entity (`customer_orders`).
- `model/OrderStatus` -> enum: `PENDING`, `CONFIRMED`, `CANCELLED`.
- `repository/OrderRepository` -> JPA access + `findByOrderNumber`.

Integration and events:
- `config/HttpClientConfig` -> `@LoadBalanced RestTemplate` bean.
- `config/KafkaTopicConfig` -> creates topics:
  - `order-created-events`
  - `inventory-events`
  - `order-events`
- `event/OrderCreatedEvent`
- `event/InventoryProcessedEvent`
- `event/OrderLifecycleEvent`
- `client/ProductResponse` -> product-service read contract.
- `client/InventoryReserveRequest`, `client/InventoryReserveResponse` -> client DTOs (currently not used in active flow).

Outbox package:
- `outbox/OutboxEvent`
- `outbox/OutboxStatus`
- `outbox/OutboxEventRepository`
- `outbox/OutboxService`
- `outbox/OutboxPublisher`

### API endpoints
- `GET /api/orders` -> list orders.
- `GET /api/orders/{orderNumber}` -> single order.
- `POST /api/orders` -> place order.

### Event behavior
- Produces (via outbox): `order-created-events` and `order-events`.
- Consumes: `inventory-events` with `@KafkaListener`.

### Data ownership
- DB: `order_db`
- Tables:
  - `customer_orders`
  - `event_outbox` (order service outbox)

---
## 7.4 notification-service
Package root: `com.ecommerce.notification`

### Purpose
Consumes final order lifecycle events and serves notification history for UI and diagnostics.

### Source map
- `NotificationServiceApplication` -> entry point.

API:
- `api/NotificationController` -> endpoints for recent notifications and single order lookup.
- `api/NotificationResponse` -> response DTO.

Core:
- `service/NotificationService` -> upsert/read notification records.
- `service/OrderNotificationConsumer` -> Kafka listener for `order-events`.
- `event/OrderLifecycleEvent` -> consumed event DTO.
- `model/NotificationEvent` -> JPA entity (`notification_events`).
- `repository/NotificationEventRepository` -> JPA + `findByOrderNumber`.

### API endpoints
- `GET /api/notifications?limit=20`
- `GET /api/notifications/{orderNumber}`

### Event behavior
- Consumes: `order-events`.
- Does not produce events.

### Data ownership
- Datasource currently points to DB: `postgres` (default DB), not `notification_db`.
- Table: `notification_events`.

Note:
- `infra/postgres/init-db.sql` creates `notification_db`, but current notification-service config uses `postgres`.
- This works, but if you want strict DB-per-service isolation, update notification-service datasource URL.

## 8. End-to-end business flow
### 8.1 Synchronous request flow
1. UI calls gateway endpoint.
2. Gateway routes to target microservice through Eureka service ID.
3. Service processes request and returns response.

### 8.2 Asynchronous Saga flow
1. Order placement:
   - `order-service` validates request and product existence.
   - Writes order row as `PENDING`.
   - Writes `OrderCreatedEvent` to order outbox.
2. Outbox relay (order-service):
   - Scheduled publisher reads pending outbox rows and publishes `order-created-events`.
3. Inventory stage:
   - `inventory-service` consumes `order-created-events`.
   - Reserves stock or determines failure.
   - Writes `InventoryProcessedEvent` to inventory outbox.
4. Outbox relay (inventory-service):
   - Publishes `inventory-events`.
5. Order finalization:
   - `order-service` consumes `inventory-events`.
   - Sets order `CONFIRMED` or `CANCELLED`.
   - Writes `OrderLifecycleEvent` to order outbox.
6. Notification:
   - `notification-service` consumes `order-events`.
   - Upserts `notification_events`.

## 9. Outbox Pattern implementation details
Implemented in:
- `order-service`
- `inventory-service`

### Why it exists
Prevents dual-write inconsistency:
- Without outbox, DB write can succeed but Kafka publish can fail.
- With outbox, event is first persisted in same DB transaction as business state.
- Async publisher guarantees eventual publish with retries.

### Outbox table model (`event_outbox`)
Fields:
- `id`
- `aggregateType`
- `aggregateId`
- `topic`
- `eventKey`
- `eventType` (Java class name for deserialization)
- `payload` (JSON)
- `status` (`PENDING`, `PUBLISHED`)
- `attempts`
- `lastError`
- `createdAt`
- `publishedAt`

### Publish relay behavior
- Scheduler interval configurable via:
  - `outbox.publisher.fixed-delay-ms` (default `1500`)
- Batch size: 50 pending rows per run.
- Publish call waits up to 10 seconds (`future.get(10, TimeUnit.SECONDS)`).
- On success:
  - status -> `PUBLISHED`
  - set `publishedAt`
  - clear `lastError`
- On failure:
  - increment `attempts`
  - persist truncated error message.

## 10. API reference
Gateway base URL: `http://localhost:8080`

Catalog:
- `GET /api/products`
- `GET /api/products/sku/{skuCode}`
- `POST /api/products`

Inventory:
- `GET /api/inventory`
- `GET /api/inventory/{skuCode}`
- `POST /api/inventory/reserve`

Orders:
- `GET /api/orders`
- `GET /api/orders/{orderNumber}`
- `POST /api/orders`

Notifications:
- `GET /api/notifications?limit={n}`
- `GET /api/notifications/{orderNumber}`

## 11. Kafka topics and message contracts
Topics:
- `order-created-events`
- `inventory-events`
- `order-events`

Contracts:
- `com.ecommerce.order.event.OrderCreatedEvent`
- `com.ecommerce.order.event.InventoryProcessedEvent`
- `com.ecommerce.order.event.OrderLifecycleEvent`
- `com.ecommerce.inventory.event.OrderCreatedEvent`
- `com.ecommerce.inventory.event.InventoryProcessedEvent`
- `com.ecommerce.notification.event.OrderLifecycleEvent`

Serialization:
- JSON serializer/deserializer.
- Type headers disabled and default types configured in `application.yml`.

## 12. Observability and operations
### Metrics
- Each service exposes `/actuator/prometheus`.
- Scrape config: `infra/prometheus/prometheus.yml`.

### Logs
- Each service writes logs under `/app/logs/*.log` inside container.
- Docker volumes mounted into `promtail`.
- Promtail pushes logs to Loki.

### Grafana
- Datasources provisioned from:
  - `infra/grafana/provisioning/datasources/datasources.yml`
- Dashboard provider config:
  - `infra/grafana/provisioning/dashboards/dashboard-provider.yml`
- Dashboard JSON files:
  - `infra/grafana/provisioning/dashboards/json/ecommerce-overview.json`
  - `infra/grafana/provisioning/dashboards/json/microservices-service-dashboard.json`

### Troubleshooting script
- `scripts/check-stack.ps1`
- Checks container state and health URLs.
- Prints logs for non-running services.

## 13. Storefront UI source reference
Files:
- `ui/storefront/index.html`
- `ui/storefront/styles.css`
- `ui/storefront/app.js`

`app.js` responsibilities:
- Fetches products, inventory, orders, notifications via gateway paths.
- Handles order submission.
- Polls order status and notification event completion.
- Updates workflow badges for Saga progress:
  - Order
  - Inventory
  - Notification
- Auto-refreshes dashboard sections every 10 seconds.

## 14. Test suite reference
Current tests are unit/web-slice focused and run with Maven Surefire.

Discovery/Gateway:
- `DiscoveryServiceApplicationTest`
- `GatewayRoutesTest`

Product:
- `ProductControllerTest`
- `ProductServiceTest`
- `CatalogBootstrapTest`

Inventory:
- `InventoryControllerTest`
- `InventoryServiceTest`
- `OrderEventConsumerTest`
- `InventoryBootstrapTest`
- `OutboxServiceTest`
- `OutboxPublisherTest`

Order:
- `OrderControllerTest`
- `OrderServiceTest`
- `KafkaTopicConfigTest`
- `OutboxServiceTest`
- `OutboxPublisherTest`

Notification:
- `NotificationControllerTest`
- `NotificationServiceTest`
- `OrderNotificationConsumerTest`

Run tests:
```bash
mvn test
```

## 15. New joiner recommended reading order
1. `docker-compose.yml` and root `README.md`.
2. `gateway-service` and `discovery-service` configs.
3. `product-service` (simple CRUD baseline).
4. `order-service/OrderService` and events.
5. `inventory-service/InventoryService` and consumer.
6. `notification-service` consumer + query APIs.
7. Outbox packages in order and inventory services.
8. Prometheus/Grafana/Loki provisioning under `infra/`.
9. `ui/storefront/app.js` for user flow visibility.
10. Test classes to understand expected behavior and guardrails.

## 16. Known design notes and tradeoffs
- Outbox relay currently retries indefinitely by periodic polling; no dead-letter table/topic yet.
- `notification-service` currently points to `postgres` DB instead of `notification_db`.
- `InventoryReserveRequest` and `InventoryReserveResponse` classes in order-service are present but not used in current Saga path.
- All Kafka topics are configured with one partition and one replica for local/dev simplicity.

## 17. Quick command reference
Start stack:
```bash
docker compose up -d --build
```

Check status:
```bash
docker compose ps -a
```

Tail service logs:
```bash
docker compose logs order-service --tail=200
docker compose logs inventory-service --tail=200
docker compose logs notification-service --tail=200
```

Health checks:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## 18. Ownership summary by bounded context
- Product context: `product-service`.
- Inventory context: `inventory-service`.
- Order context: `order-service`.
- Notification/read model context: `notification-service`.
- Edge routing: `gateway-service`.
- Service registration: `discovery-service`.
- Observability and runtime infrastructure: `infra/` + compose services.

---
If you modify business workflows or event contracts, update this document in the same PR to keep onboarding accurate.
