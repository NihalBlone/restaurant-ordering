package com.nihal.restaurantordering.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderRequestItem(
        @NotNull(message = "menuItemId is required")
        UUID menuItemId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {
}
