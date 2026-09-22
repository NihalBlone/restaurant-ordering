package com.nihal.restaurantordering.dto.session;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TableSessionCloseResponse(
        UUID tableId,
        String tableNumber,
        UUID restaurantId,
        UUID closedSessionId,
        Integer totalItems,
        BigDecimal totalAmount,
        OffsetDateTime closedAt
) {
}
