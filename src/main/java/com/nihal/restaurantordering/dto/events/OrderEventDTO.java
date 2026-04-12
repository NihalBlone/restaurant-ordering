package com.nihal.restaurantordering.dto.events;

import com.nihal.restaurantordering.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderEventDTO(
        UUID orderId,
        UUID tableId,
        UUID restaurantId,
        UUID sessionId,
        OrderStatus status,
        List<OrderEventItemDTO> items,
        BigDecimal totalAmount,
        OffsetDateTime timestamp
) {
}
