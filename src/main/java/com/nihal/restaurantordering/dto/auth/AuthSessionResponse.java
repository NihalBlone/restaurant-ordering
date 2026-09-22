package com.nihal.restaurantordering.dto.auth;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AuthSessionResponse(
        UUID adminId,
        UUID restaurantId,
        String username,
        String email,
        String restaurantName,
        String restaurantLocation,
        String role
) {
}
