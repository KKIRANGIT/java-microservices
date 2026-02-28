package com.ecommerce.inventory.api;

import jakarta.validation.constraints.Min;

public record UpdateInventoryRequest(
        @Min(value = 0, message = "quantity must be zero or greater")
        int quantity) {
}
