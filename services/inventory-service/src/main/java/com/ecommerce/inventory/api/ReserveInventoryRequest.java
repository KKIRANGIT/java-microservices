package com.ecommerce.inventory.api;

public record ReserveInventoryRequest(String skuCode, int quantity) {
}
