package com.ecommerce.order.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderLifecycleEvent(
        String orderNumber,
        String skuCode,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String customerEmail,
        String orderStatus,
        String message,
        Instant orderCreatedAt,
        Instant occurredAt) {
}
