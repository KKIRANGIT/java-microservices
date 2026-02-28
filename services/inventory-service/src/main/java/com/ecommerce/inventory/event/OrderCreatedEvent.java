package com.ecommerce.inventory.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        String orderNumber,
        String skuCode,
        int quantity,
        String productName,
        BigDecimal totalPrice,
        String customerEmail,
        Instant createdAt) {
}
