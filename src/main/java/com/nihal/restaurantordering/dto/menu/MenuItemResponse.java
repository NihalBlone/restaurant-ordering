package com.nihal.restaurantordering.dto.menu;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record MenuItemResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean vegetarian
) {
}
