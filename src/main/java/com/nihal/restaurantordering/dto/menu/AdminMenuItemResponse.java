package com.nihal.restaurantordering.dto.menu;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AdminMenuItemResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        Integer categoryDisplayOrder,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean vegetarian,
        boolean available
) {
}
