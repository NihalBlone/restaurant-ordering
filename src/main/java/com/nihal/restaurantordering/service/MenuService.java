package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.menu.MenuCategoryResponse;
import com.nihal.restaurantordering.dto.menu.MenuItemResponse;
import com.nihal.restaurantordering.dto.menu.MenuResponse;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final RestaurantContextService restaurantContextService;
    private final MenuItemRepository menuItemRepository;

    public MenuResponse getMenuByTable(UUID tableId) {
        RestaurantTable table = restaurantContextService.getActiveTable(tableId);
        Restaurant restaurant = restaurantContextService.getRestaurant(table.getRestaurantId());

        List<MenuItemRepository.MenuCategoryItemProjection> menuRows =
                menuItemRepository.findMenuCategoryItemsByRestaurantId(restaurant.getId());
        List<MenuCategoryResponse> categoryResponses = mapCategoryResponses(menuRows);

        return MenuResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .restaurantLocation(restaurant.getLocation())
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .categories(categoryResponses)
                .build();
    }

    private List<MenuCategoryResponse> mapCategoryResponses(List<MenuItemRepository.MenuCategoryItemProjection> menuRows) {
        Map<UUID, MenuCategoryAccumulator> categories = new LinkedHashMap<>();
        for (MenuItemRepository.MenuCategoryItemProjection row : menuRows) {
            MenuCategoryAccumulator category = categories.computeIfAbsent(row.getCategoryId(),
                    ignored -> new MenuCategoryAccumulator(row.getCategoryId(), row.getCategoryName(), row.getCategoryDisplayOrder()));
            if (row.getItemId() != null) {
                category.items().add(MenuItemResponse.builder()
                        .id(row.getItemId())
                        .name(row.getItemName())
                        .description(row.getItemDescription())
                        .price(row.getItemPrice())
                        .imageUrl(row.getItemImageUrl())
                        .vegetarian(Boolean.TRUE.equals(row.getItemVegetarian()))
                        .build());
            }
        }

        return categories.values().stream()
                .map(category -> MenuCategoryResponse.builder()
                        .id(category.id())
                        .name(category.name())
                        .displayOrder(category.displayOrder())
                        .items(List.copyOf(category.items()))
                        .build())
                .toList();
    }

    private record MenuCategoryAccumulator(
            UUID id,
            String name,
            Integer displayOrder,
            List<MenuItemResponse> items
    ) {
        private MenuCategoryAccumulator(UUID id, String name, Integer displayOrder) {
            this(id, name, displayOrder, new ArrayList<>());
        }
    }
}
