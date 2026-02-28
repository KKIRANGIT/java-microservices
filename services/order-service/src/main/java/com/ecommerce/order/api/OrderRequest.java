package com.ecommerce.order.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderRequest(
        @NotBlank(message = "skuCode is required")
        String skuCode,
        @Min(value = 1, message = "quantity must be greater than zero")
        int quantity,
        @NotBlank(message = "customerEmail is required")
        @Email(message = "customerEmail must be a valid email address")
        String customerEmail) {
}
