package com.nihal.restaurantordering.dto.events;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TableSessionEventDTO(
        String type,
        UUID tableId,
        UUID restaurantId,
        UUID sessionId,
        OffsetDateTime timestamp
) {
}
