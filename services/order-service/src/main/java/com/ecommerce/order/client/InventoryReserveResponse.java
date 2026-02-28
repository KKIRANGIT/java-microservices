package com.ecommerce.order.client;

public record InventoryReserveResponse(String skuCode, boolean available, int quantity) {
}
