package com.ecommerce.product.api;

import java.math.BigDecimal;

public record ProductResponse(Long id, String skuCode, String name, String description, BigDecimal price) {
}
