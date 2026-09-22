package com.nihal.restaurantordering.dto.report;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TableSalesSummary(
        UUID tableId,
        String tableNumber,
        long settledBills,
        long totalOrders,
        long totalItems,
        BigDecimal totalAmount
) {
}
