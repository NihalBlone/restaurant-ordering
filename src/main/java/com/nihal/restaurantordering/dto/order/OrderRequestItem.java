package com.nihal.restaurantordering.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderRequestItem(
        @NotNull(message = "menuItemId is required")
        UUID menuItemId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity
) {
}
