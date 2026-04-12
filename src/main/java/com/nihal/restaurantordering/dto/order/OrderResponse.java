package com.nihal.restaurantordering.dto.order;

import com.nihal.restaurantordering.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID orderId,
        UUID restaurantId,
        UUID tableId,
        String customerName,
        OrderStatus status,
        Integer totalItems,
        BigDecimal estimatedTotalAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items
) {
}
