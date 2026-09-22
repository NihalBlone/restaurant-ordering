package com.nihal.restaurantordering.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "username is required")
        @Size(max = 80, message = "username must be at most 80 characters")
        String username
) {
}
