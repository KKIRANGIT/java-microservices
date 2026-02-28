package com.ecommerce.inventory.service;

import static org.mockito.Mockito.verify;

import com.ecommerce.inventory.event.OrderCreatedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderEventConsumerTest {

    @Test
    void handleOrderCreated_delegatesToInventoryService() {
        InventoryService inventoryService = Mockito.mock(InventoryService.class);
        OrderEventConsumer consumer = new OrderEventConsumer(inventoryService);
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ORD-1",
                "SKU-1",
                2,
                "Product",
                new BigDecimal("100.00"),
                "user@example.com",
                Instant.now());

        consumer.handleOrderCreated(event);

        verify(inventoryService).handleOrderCreated(event);
    }
}
