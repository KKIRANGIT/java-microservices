package com.ecommerce.inventory.api;

public record InventoryResponse(String skuCode, boolean available, int quantity) {
}
