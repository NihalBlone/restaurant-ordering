package com.nihal.restaurantordering.dto.menu;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AdminMenuCategoryResponse(
        UUID id,
        String name,
        Integer displayOrder
) {
}
