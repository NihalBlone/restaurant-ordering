package com.nihal.restaurantordering.dto.order;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record OrdersResponse(
        UUID tableId,
        String tableNumber,
        UUID sessionId,
        boolean sessionActive,
        Integer sessionTotalItems,
        BigDecimal sessionTotalAmount,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<OrderResponse> orders
) {
}
