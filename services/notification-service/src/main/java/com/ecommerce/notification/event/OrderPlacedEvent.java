package com.ecommerce.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPlacedEvent(
        String orderNumber,
        String skuCode,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String customerEmail,
        Instant createdAt) {
}
