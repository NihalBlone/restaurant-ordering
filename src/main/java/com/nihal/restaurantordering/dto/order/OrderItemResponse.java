package com.nihal.restaurantordering.dto.order;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderItemResponse(
        UUID orderItemId,
        UUID menuItemId,
        String menuItemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
