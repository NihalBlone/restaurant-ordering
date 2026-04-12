package com.nihal.restaurantordering.dto.events;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderEventItemDTO(
        UUID menuItemId,
        String menuItemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
