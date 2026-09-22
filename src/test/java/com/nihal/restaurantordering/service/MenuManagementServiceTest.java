package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.menu.AdminMenuItemResponse;
import com.nihal.restaurantordering.dto.menu.CreateMenuCategoryRequest;
import com.nihal.restaurantordering.dto.menu.CreateMenuItemRequest;
import com.nihal.restaurantordering.dto.menu.MenuItemResponse;
import com.nihal.restaurantordering.dto.menu.MenuResponse;
import com.nihal.restaurantordering.dto.menu.UpdateMenuItemRequest;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MenuManagementServiceTest {

    @Autowired
    private MenuManagementService menuManagementService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Test
    void updateMenuItemPersistsAdminFieldsAndEvictsTheCustomerMenuCache() {
        Restaurant restaurant = restaurantRepository.findAll().get(0);
        RestaurantTable table = restaurantTableRepository.findAll().get(0);
        AdminMenuItemResponse existingItem = menuManagementService.getMenuItems(restaurant.getId()).get(0);

        menuService.getMenuByTable(table.getId());

        AdminMenuItemResponse updatedItem = menuManagementService.updateMenuItem(
                restaurant.getId(),
                existingItem.id(),
                new UpdateMenuItemRequest(
                        "Updated dish",
                        "A clearer customer-facing description",
                        new BigDecimal("199.50"),
                        "https://images.example.com/updated-dish.jpg",
                        false,
                        true
                )
        );

        assertThat(updatedItem.name()).isEqualTo("Updated dish");
        assertThat(updatedItem.imageUrl()).isEqualTo("https://images.example.com/updated-dish.jpg");
        assertThat(updatedItem.vegetarian()).isFalse();

        MenuResponse refreshedMenu = menuService.getMenuByTable(table.getId());
        MenuItemResponse customerItem = refreshedMenu.categories().stream()
                .flatMap(category -> category.items().stream())
                .filter(item -> item.id().equals(existingItem.id()))
                .findFirst()
                .orElseThrow();

        assertThat(customerItem.name()).isEqualTo("Updated dish");
        assertThat(customerItem.price()).isEqualByComparingTo("199.50");
        assertThat(customerItem.imageUrl()).isEqualTo("https://images.example.com/updated-dish.jpg");
        assertThat(customerItem.vegetarian()).isFalse();
    }

    @Test
    void createCategoryAndDishMakesNewTabAvailableToCustomers() {
        Restaurant restaurant = restaurantRepository.findAll().get(0);
        RestaurantTable table = restaurantTableRepository.findAll().get(0);

        var category = menuManagementService.createMenuCategory(
                restaurant.getId(),
                new CreateMenuCategoryRequest("Desserts", null)
        );
        var item = menuManagementService.createMenuItem(
                restaurant.getId(),
                new CreateMenuItemRequest(
                        category.id(),
                        "Chocolate Brownie",
                        "Warm brownie with dark chocolate",
                        new BigDecimal("189.00"),
                        "",
                        true,
                        true
                )
        );

        MenuResponse customerMenu = menuService.getMenuByTable(table.getId());
        assertThat(customerMenu.categories())
                .anySatisfy(menuCategory -> {
                    assertThat(menuCategory.name()).isEqualTo("Desserts");
                    assertThat(menuCategory.items())
                            .extracting(MenuItemResponse::id)
                            .contains(item.id());
                });
    }
}
