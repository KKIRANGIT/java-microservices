package com.ecommerce.order.service;

import com.ecommerce.order.api.OrderRequest;
import com.ecommerce.order.api.OrderResponse;
import com.ecommerce.order.client.InventoryReserveRequest;
import com.ecommerce.order.client.InventoryReserveResponse;
import com.ecommerce.order.client.ProductResponse;
import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.model.CustomerOrder;
import com.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class OrderService {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public OrderService(
            OrderRepository orderRepository,
            RestTemplate restTemplate,
            KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    public List<OrderResponse> getOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request.quantity() <= 0) {
            throw new OrderPlacementException("Quantity must be greater than zero");
        }
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new OrderPlacementException("Customer email is required");
        }
        ProductResponse product;
        try {
            product = restTemplate.getForObject(
                    "http://product-service/api/products/sku/{skuCode}",
                    ProductResponse.class,
                    request.skuCode());
        } catch (RestClientException ex) {
            throw new OrderPlacementException("Product not found for sku " + request.skuCode());
        }
        if (product == null) {
            throw new OrderPlacementException("Product not found for sku " + request.skuCode());
        }

        InventoryReserveResponse inventory;
        try {
            inventory = restTemplate.postForObject(
                    "http://inventory-service/api/inventory/reserve",
                    new InventoryReserveRequest(request.skuCode(), request.quantity()),
                    InventoryReserveResponse.class);
        } catch (RestClientException ex) {
            throw new OrderPlacementException("Inventory service unavailable");
        }

        if (inventory == null || !inventory.available()) {
            throw new OrderPlacementException("Inventory unavailable for sku " + request.skuCode());
        }

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setSkuCode(product.skuCode());
        order.setProductName(product.name());
        order.setQuantity(request.quantity());
        order.setUnitPrice(product.price());
        order.setTotalPrice(product.price().multiply(BigDecimal.valueOf(request.quantity())));
        order.setCustomerEmail(request.customerEmail());
        order.setCreatedAt(Instant.now());

        CustomerOrder savedOrder = orderRepository.save(order);

        kafkaTemplate.send(
                ORDER_EVENTS_TOPIC,
                savedOrder.getOrderNumber(),
                new OrderPlacedEvent(
                        savedOrder.getOrderNumber(),
                        savedOrder.getSkuCode(),
                        savedOrder.getProductName(),
                        savedOrder.getQuantity(),
                        savedOrder.getTotalPrice(),
                        savedOrder.getCustomerEmail(),
                        savedOrder.getCreatedAt()));

        return toResponse(savedOrder);
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
                order.getCreatedAt());
    }
}
