package com.ecommerce.order.client;

public record InventoryReserveRequest(String skuCode, int quantity) {
}
