package com.ecommerce.product.api;

import java.math.BigDecimal;

public record ProductRequest(String skuCode, String name, String description, BigDecimal price) {
}
