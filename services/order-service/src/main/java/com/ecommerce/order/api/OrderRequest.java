package com.ecommerce.order.api;

public record OrderRequest(String skuCode, int quantity, String customerEmail) {
}
