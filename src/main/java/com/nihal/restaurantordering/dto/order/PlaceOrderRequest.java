package com.nihal.restaurantordering.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull(message = "tableId is required")
        UUID tableId,

        UUID sessionId,

        @NotBlank(message = "customerName is required")
        @Size(max = 80, message = "customerName must not exceed 80 characters")
        String customerName,

        @NotEmpty(message = "items must not be empty")
        List<@Valid OrderRequestItem> items
) {
}
