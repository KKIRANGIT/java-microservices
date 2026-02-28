package com.ecommerce.order.service;

import com.ecommerce.order.api.OrderRequest;
import com.ecommerce.order.api.OrderResponse;
import com.ecommerce.order.client.ProductResponse;
import com.ecommerce.order.event.InventoryProcessedEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderLifecycleEvent;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.outbox.OutboxService;
import com.ecommerce.order.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);
    private static final String ORDER_CREATED_TOPIC = "order-created-events";
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;

    public OrderService(
            OrderRepository orderRepository,
            ProductCatalogClient productCatalogClient,
            OutboxService outboxService,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
        Gauge.builder("ecommerce.orders.pending", orderRepository, repo -> repo.countByStatus(OrderStatus.PENDING.name()))
                .description("Current number of pending orders waiting for inventory result")
                .register(meterRegistry);
    }

    public List<OrderResponse> getOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    public OrderResponse getOrderByNumber(@NotBlank(message = "orderNumber is required") String orderNumber) {
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderPlacementException("Order not found for orderNumber " + orderNumber));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse placeOrder(@Valid OrderRequest request) {
        if (request.quantity() <= 0) {
            meterRegistry.counter("ecommerce.orders.rejected", "reason", "invalid_quantity").increment();
            throw new OrderPlacementException("Quantity must be greater than zero");
        }
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            meterRegistry.counter("ecommerce.orders.rejected", "reason", "invalid_email").increment();
            throw new OrderPlacementException("Customer email is required");
        }

        ProductResponse product;
        try {
            product = productCatalogClient.getProductBySku(request.skuCode());
        } catch (ProductNotFoundException ex) {
            meterRegistry.counter("ecommerce.orders.rejected", "reason", "product_not_found").increment();
            throw new OrderPlacementException(ex.getMessage());
        } catch (ProductServiceUnavailableException ex) {
            meterRegistry.counter("ecommerce.orders.rejected", "reason", "product_service_unavailable").increment();
            throw new OrderPlacementException("Product service is temporarily unavailable. Please retry.");
        } catch (RuntimeException ex) {
            meterRegistry.counter("ecommerce.orders.rejected", "reason", "product_lookup_throttled").increment();
            throw new OrderPlacementException("Product lookup is temporarily throttled. Please retry.");
        }

        Instant now = Instant.now();
        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setSkuCode(product.skuCode());
        order.setProductName(product.name());
        order.setQuantity(request.quantity());
        order.setUnitPrice(product.price());
        order.setTotalPrice(product.price().multiply(BigDecimal.valueOf(request.quantity())));
        order.setCustomerEmail(request.customerEmail());
        order.setStatus(OrderStatus.PENDING.name());
        order.setFailureReason(null);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        CustomerOrder savedOrder = orderRepository.save(order);

        outboxService.enqueue(
                "ORDER",
                savedOrder.getOrderNumber(),
                ORDER_CREATED_TOPIC,
                savedOrder.getOrderNumber(),
                new OrderCreatedEvent(
                        savedOrder.getOrderNumber(),
                        savedOrder.getSkuCode(),
                        savedOrder.getQuantity(),
                        savedOrder.getProductName(),
                        savedOrder.getTotalPrice(),
                        savedOrder.getCustomerEmail(),
                        savedOrder.getCreatedAt()));
        LOGGER.info(
                "Enqueued order-created event in outbox: orderNumber={}, skuCode={}, quantity={}",
                savedOrder.getOrderNumber(),
                savedOrder.getSkuCode(),
                savedOrder.getQuantity());
        meterRegistry.counter("ecommerce.orders.placed").increment();

        return toResponse(savedOrder);
    }

    @KafkaListener(topics = INVENTORY_EVENTS_TOPIC, groupId = "order-service")
    @Transactional
    public void handleInventoryEvent(InventoryProcessedEvent event) {
        LOGGER.info(
                "Received inventory event: orderNumber={}, skuCode={}, reserved={}, message={}",
                event.orderNumber(),
                event.skuCode(),
                event.reserved(),
                event.message());

        CustomerOrder order = orderRepository.findByOrderNumber(event.orderNumber()).orElse(null);
        if (order == null) {
            LOGGER.warn("Ignoring inventory event for unknown orderNumber={}", event.orderNumber());
            return;
        }

        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            LOGGER.info(
                    "Ignoring inventory event because order is already finalized: orderNumber={}, status={}",
                    order.getOrderNumber(),
                    order.getStatus());
            return;
        }

        String lifecycleStatus;
        String lifecycleMessage;

        if (event.reserved()) {
            order.setStatus(OrderStatus.CONFIRMED.name());
            order.setFailureReason(null);
            lifecycleStatus = OrderStatus.CONFIRMED.name();
            lifecycleMessage = "Inventory reserved. Remaining quantity: " + event.availableQuantity();
        } else {
            order.setStatus(OrderStatus.CANCELLED.name());
            order.setFailureReason(event.message());
            lifecycleStatus = OrderStatus.CANCELLED.name();
            lifecycleMessage = event.message();
        }

        order.setUpdatedAt(Instant.now());
        CustomerOrder updated = orderRepository.save(order);

        outboxService.enqueue(
                "ORDER",
                updated.getOrderNumber(),
                ORDER_EVENTS_TOPIC,
                updated.getOrderNumber(),
                new OrderLifecycleEvent(
                        updated.getOrderNumber(),
                        updated.getSkuCode(),
                        updated.getProductName(),
                        updated.getQuantity(),
                        updated.getTotalPrice(),
                        updated.getCustomerEmail(),
                        lifecycleStatus,
                        lifecycleMessage,
                        updated.getCreatedAt(),
                        updated.getUpdatedAt()));
        LOGGER.info(
                "Enqueued order lifecycle event in outbox: orderNumber={}, status={}, reason={}",
                updated.getOrderNumber(),
                lifecycleStatus,
                updated.getFailureReason());
        meterRegistry.counter("ecommerce.orders.finalized", "status", lifecycleStatus).increment();
        recordOrderLifecycleDuration(updated, lifecycleStatus);
    }

    private void recordOrderLifecycleDuration(CustomerOrder order, String lifecycleStatus) {
        if (order.getCreatedAt() == null || order.getUpdatedAt() == null) {
            return;
        }
        Duration duration = Duration.between(order.getCreatedAt(), order.getUpdatedAt());
        if (duration.isNegative()) {
            return;
        }
        Timer.builder("ecommerce.orders.lifecycle.duration")
                .description("Order lifecycle duration from creation to final status")
                .tag("status", lifecycleStatus)
                .register(meterRegistry)
                .record(duration);
    }

    private OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getOrderNumber(),
                order.getSkuCode(),
                order.getProductName(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalPrice(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
