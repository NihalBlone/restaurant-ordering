package com.nihal.restaurantordering.dto.order;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record OrdersResponse(
        UUID tableId,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<OrderResponse> orders
) {
}
