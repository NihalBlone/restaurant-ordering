package com.nihal.restaurantordering.dto.order;

import com.nihal.restaurantordering.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {
}
