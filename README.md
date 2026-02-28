# Ecommerce Microservices (Spring Boot + Spring Cloud + Kafka + Postgres)

Production-style local stack with:

- Spring Boot microservices (`product`, `inventory`, `order`, `notification`)
- Spring Cloud (`discovery-service` with Eureka, `gateway-service`)
- Kafka event flow with Saga topics (`order-created-events`, `inventory-events`, `order-events`)
- Postgres persistence for product, inventory, order, and notification data
- Observability (`Prometheus`, `Grafana`, `Loki`, `Promtail`)
- UI (`storefront` on Nginx, Kafka UI, Grafana, Eureka dashboard)
- One-command startup via Docker Compose

## Architecture

- `discovery-service` (`8761`): Eureka service registry
- `gateway-service` (`8080`): API Gateway routes `/api/products`, `/api/inventory`, `/api/orders`, `/api/notifications`
- `product-service` (`8081`): Product catalog API with seeded items
- `inventory-service` (`8082`): Stock lookup/reservation and inventory event producer for Saga
- `order-service` (`8083`): Saga orchestrator; creates `PENDING` order, consumes inventory result events, and publishes final order lifecycle events
- `notification-service` (`8084`): Kafka consumer for final order lifecycle events and notification query APIs
- `postgres` (`5432`): `product_db`, `inventory_db`, `order_db` (+ notification event tables)
- `zookeeper` + `kafka` (`9092`) and `kafka-ui` (`8090`)
- `prometheus` (`9090`)
- `grafana` (`3000`, `admin/admin`) pre-provisioned with Prometheus + Loki
- `loki` (`3100`) + `promtail`
- `storefront` (`5173`)

## Run

```bash
docker compose up -d --build
```

Then open:

- Storefront: http://localhost:5173
- Gateway API base: http://localhost:8080
- Eureka: http://localhost:8761
- Kafka UI: http://localhost:8090
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000

## Troubleshooting

```powershell
# Show container status + logs for failed services
powershell -ExecutionPolicy Bypass -File .\scripts\check-stack.ps1
```

```bash
# Rebuild only application services
docker compose build discovery-service gateway-service product-service inventory-service order-service notification-service
docker compose up -d
```

```bash
# If Kafka shows InconsistentClusterIdException, reset Kafka/Zookeeper volumes once
docker compose down -v
docker compose up -d --build
```

## API Quick Check

```bash
# List products
curl http://localhost:8080/api/products

# Place order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"skuCode\":\"LAPTOP-ACER-001\",\"quantity\":1,\"customerEmail\":\"user@example.com\"}"

# List orders
curl http://localhost:8080/api/orders
```

## Project Layout

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
  loki/
  promtail/
  grafana/
ui/
  storefront/
docker-compose.yml
```

## Per-Service Monitoring (Grafana + Prometheus)

After `docker compose up -d --build`:

- Grafana dashboard: `http://localhost:3000` -> `Ecommerce` folder -> `Microservices Service Dashboard`
- Prometheus targets: `http://localhost:9090/targets`

Useful Prometheus queries (each line shows all services by `job`):

- `up{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service"}`
- `sum(rate(http_server_requests_seconds_count{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service"}[1m])) by (job)`
- `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service"}[5m])) by (le,job))`
- `sum(rate(http_server_requests_seconds_count{job=~"discovery-service|gateway-service|product-service|inventory-service|order-service|notification-service",status=~"5.."}[5m])) by (job)`

## Post-Order End-to-End Test Flow

After placing an order in the UI (`http://localhost:5173`), validate all downstream services with these steps:

1. Order service processing
- In UI `Recent Orders`, confirm a new row appears with `orderNumber`.
- API check: `curl http://localhost:8080/api/orders`

2. Inventory service processing
- In UI `Inventory Snapshot`, confirm selected SKU quantity is reduced.
- API check (single SKU): `curl http://localhost:8080/api/inventory/LAPTOP-ACER-001`
- API check (all SKUs): `curl http://localhost:8080/api/inventory`

3. Notification service processing
- In UI `Notification Events`, confirm the same `orderNumber` appears with status `NOTIFICATION_PROCESSED`.
- API check (recent): `curl http://localhost:8080/api/notifications?limit=20`
- API check (specific order): `curl http://localhost:8080/api/notifications/{orderNumber}`

4. Kafka event verification
- Open Kafka UI: `http://localhost:8090`
- Topic: `order-events`
- Verify new order event messages are present.

5. Metrics/monitoring verification
- Prometheus targets: `http://localhost:9090/targets` (all services should be `UP`)
- Grafana dashboard: `Ecommerce -> Microservices Service Dashboard`

If `notification-service` is empty after placing order, wait 5-10 seconds and refresh Notification Events because Kafka consumption is asynchronous.

## Saga-Style Kafka Flow

Order flow is event-driven:

1. `order-service` writes order as `PENDING` and publishes `order-created-events`.
2. `inventory-service` consumes `order-created-events`, tries reservation, then publishes `inventory-events` with success/failure.
3. `order-service` consumes `inventory-events` and updates order status to `CONFIRMED` or `CANCELLED`.
4. `order-service` publishes final lifecycle event to `order-events`.
5. `notification-service` consumes `order-events` and persists final notification status.

Expected Kafka topics in Kafka UI after placing orders:
- `order-created-events`
- `inventory-events`
- `order-events`

