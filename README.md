# Ecommerce Microservices (Spring Boot + Spring Cloud + Kafka + Postgres)

Production-style local stack with:

- Spring Boot microservices (`product`, `inventory`, `order`, `notification`)
- Spring Cloud (`discovery-service` with Eureka, `gateway-service`)
- Kafka event flow (`order-events`)
- Postgres persistence (separate DB per domain)
- Observability (`Prometheus`, `Grafana`, `Loki`, `Promtail`)
- UI (`storefront` on Nginx, Kafka UI, Grafana, Eureka dashboard)
- One-command startup via Docker Compose

## Architecture

- `discovery-service` (`8761`): Eureka service registry
- `gateway-service` (`8080`): API Gateway routes `/api/products`, `/api/inventory`, `/api/orders`
- `product-service` (`8081`): Product catalog API with seeded items
- `inventory-service` (`8082`): Stock lookup and reservation
- `order-service` (`8083`): Checkout API, validates product, reserves inventory, stores order, publishes Kafka event
- `notification-service` (`8084`): Kafka consumer logs order events
- `postgres` (`5432`): `product_db`, `inventory_db`, `order_db`
- `kafka` (`9092`) and `kafka-ui` (`8090`)
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
