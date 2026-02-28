package com.ecommerce.order.client;

import java.math.BigDecimal;

public record ProductResponse(Long id, String skuCode, String name, String description, BigDecimal price) {
}
