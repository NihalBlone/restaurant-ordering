package com.nihal.restaurantordering.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemSalesSummary(UUID menuItemId, String name, long totalItems, BigDecimal totalAmount) {
}
