package com.nihal.restaurantordering.dto.report;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record SalesReportResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        UUID tableId,
        UUID menuItemId,
        String timeZone,
        long settledBills,
        long totalOrders,
        long totalItems,
        BigDecimal totalAmount,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<TableSalesSummary> tables,
        List<ItemSalesSummary> items,
        List<DailySalesSummary> days,
        List<SalesStatementEntry> statements
) {
}
