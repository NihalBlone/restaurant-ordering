package com.nihal.restaurantordering.dto.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMenuCategoryRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Positive(message = "displayOrder must be greater than zero")
        Integer displayOrder
) {
}
