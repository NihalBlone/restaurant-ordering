package com.nihal.restaurantordering.dto.menu;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMenuItemRequest(
        @NotNull(message = "categoryId is required")
        UUID categoryId,

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer digits and 2 decimal places")
        BigDecimal price,

        @Size(max = 1000, message = "imageUrl must be at most 1000 characters")
        @Pattern(regexp = "^$|https?://.+|/uploads/menu/[A-Za-z0-9._-]+$",
                message = "imageUrl must be an HTTP(S) URL or an uploaded menu image")
        String imageUrl,

        @NotNull(message = "vegetarian is required")
        Boolean vegetarian,

        @NotNull(message = "available is required")
        Boolean available
) {
}
