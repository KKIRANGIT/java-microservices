package com.ecommerce.inventory.service;

import com.ecommerce.inventory.event.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    public OrderEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "order-created-events", groupId = "inventory-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        inventoryService.handleOrderCreated(event);
    }
}
