package com.ecommerce.order.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderNumber,
        String skuCode,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String customerEmail,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {
}
