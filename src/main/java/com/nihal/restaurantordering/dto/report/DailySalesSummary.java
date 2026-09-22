package com.nihal.restaurantordering.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesSummary(LocalDate date, long totalItems, BigDecimal totalAmount) {
}
