package com.nihal.restaurantordering.dto.admin;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AdminTableResponse(
        UUID id,
        String tableNumber,
        String qrCodeUrl,
        boolean active,
        UUID currentSessionId
) {
}
