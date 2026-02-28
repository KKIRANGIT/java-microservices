package com.ecommerce.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReserveInventoryRequest(
        @NotBlank(message = "skuCode is required")
        @Size(max = 64, message = "skuCode must be at most 64 characters")
        String skuCode,
        @Min(value = 1, message = "quantity must be greater than zero")
        int quantity) {
}
