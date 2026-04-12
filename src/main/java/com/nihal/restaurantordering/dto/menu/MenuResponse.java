package com.nihal.restaurantordering.dto.menu;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MenuResponse(
        UUID restaurantId,
        String restaurantName,
        String restaurantLocation,
        UUID tableId,
        String tableNumber,
        List<MenuCategoryResponse> categories
) {
}
