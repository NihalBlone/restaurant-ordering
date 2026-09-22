package com.nihal.restaurantordering.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "token is required")
        @Size(max = 200, message = "token must be at most 200 characters")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 10, max = 72, message = "newPassword must be between 10 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "newPassword must include uppercase, lowercase, number, and special characters"
        )
        String newPassword
) {
}
