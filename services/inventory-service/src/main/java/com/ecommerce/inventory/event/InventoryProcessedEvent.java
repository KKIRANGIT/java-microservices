package com.ecommerce.inventory.event;

import java.time.Instant;

public record InventoryProcessedEvent(
        String orderNumber,
        String skuCode,
        int requestedQuantity,
        int availableQuantity,
        boolean reserved,
        String message,
        Instant processedAt) {
}
