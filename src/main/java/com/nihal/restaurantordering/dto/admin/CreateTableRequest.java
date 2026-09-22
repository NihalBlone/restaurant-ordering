package com.nihal.restaurantordering.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateTableRequest(
        @NotBlank(message = "Table name is required")
        @Size(max = 30, message = "Table name must be 30 characters or fewer")
        @Pattern(regexp = "[\\p{L}\\p{N}][\\p{L}\\p{N} _-]*",
                message = "Use letters, numbers, spaces, hyphens or underscores for the table name")
        String tableNumber
) {
    public CreateTableRequest {
        if (tableNumber != null) {
            tableNumber = tableNumber.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        }
    }
}
