package com.nihal.restaurantordering.dto.report;

import com.nihal.restaurantordering.dto.order.OrderResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record SalesStatementEntry(
        UUID sessionId,
        UUID tableId,
        String tableNumber,
        OffsetDateTime openedAt,
        OffsetDateTime settledAt,
        long totalOrders,
        long totalItems,
        BigDecimal totalAmount,
        BigDecimal billTotalAmount,
        List<OrderResponse> orders
) {
}
