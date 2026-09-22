package com.nihal.restaurantordering.dto.auth;

import lombok.Builder;

@Builder
public record PasswordResetRequestResponse(
        String message,
        String developmentResetToken
) {
}
