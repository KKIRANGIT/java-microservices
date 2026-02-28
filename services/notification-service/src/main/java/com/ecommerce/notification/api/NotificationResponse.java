package com.ecommerce.notification.api;

import java.math.BigDecimal;
import java.time.Instant;

public record NotificationResponse(
        String orderNumber,
        String skuCode,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String customerEmail,
        Instant orderCreatedAt,
        String status,
        String message,
        Instant processedAt) {
}
