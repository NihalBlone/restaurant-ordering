package com.nihal.restaurantordering.dto.menu;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MenuCategoryResponse(
        UUID id,
        String name,
        Integer displayOrder,
        List<MenuItemResponse> items
) {
}
