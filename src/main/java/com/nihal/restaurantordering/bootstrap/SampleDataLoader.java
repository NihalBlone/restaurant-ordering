package com.nihal.restaurantordering.bootstrap;

import com.nihal.restaurantordering.domain.MenuCategory;
import com.nihal.restaurantordering.domain.MenuItem;
import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.repository.MenuCategoryRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SampleDataLoader implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;

    @Override
    public void run(String... args) {
        if (restaurantRepository.count() > 0) {
            return;
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName("Spice Garden");
        restaurant.setLocation("Bengaluru, India");
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        List<RestaurantTable> tables = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            RestaurantTable table = new RestaurantTable();
            table.setRestaurantId(savedRestaurant.getId());
            table.setTableNumber("T" + i);
            table.setQrCodeUrl("https://yourapp.com/menu?tableId=TABLE-" + i);
            table.setActive(true);
            tables.add(table);
        }
        List<RestaurantTable> savedTables = restaurantTableRepository.saveAll(tables);
        savedTables.forEach(table -> {
            table.setQrCodeUrl("https://yourapp.com/menu?tableId=" + table.getId());
            restaurantTableRepository.save(table);
        });

        MenuCategory starters = saveCategory(savedRestaurant.getId(), "Starters", 1);
        MenuCategory mains = saveCategory(savedRestaurant.getId(), "Main Course", 2);
        MenuCategory beverages = saveCategory(savedRestaurant.getId(), "Beverages", 3);

        menuItemRepository.saveAll(List.of(
                createMenuItem(savedRestaurant.getId(), starters.getId(), "Paneer Tikka", "Char-grilled cottage cheese with spices", new BigDecimal("249.00")),
                createMenuItem(savedRestaurant.getId(), starters.getId(), "Veg Spring Rolls", "Crisp rolls with sweet chili dip", new BigDecimal("179.00")),
                createMenuItem(savedRestaurant.getId(), mains.getId(), "Butter Naan", "Soft tandoor-baked flatbread", new BigDecimal("49.00")),
                createMenuItem(savedRestaurant.getId(), mains.getId(), "Paneer Butter Masala", "Rich tomato gravy with paneer", new BigDecimal("289.00")),
                createMenuItem(savedRestaurant.getId(), mains.getId(), "Veg Biryani", "Fragrant basmati rice with vegetables", new BigDecimal("269.00")),
                createMenuItem(savedRestaurant.getId(), beverages.getId(), "Fresh Lime Soda", "Sweet or salted lime cooler", new BigDecimal("99.00")),
                createMenuItem(savedRestaurant.getId(), beverages.getId(), "Masala Chai", "House-brewed spiced tea", new BigDecimal("59.00"))
        ));
    }

    private MenuCategory saveCategory(java.util.UUID restaurantId, String name, int displayOrder) {
        MenuCategory category = new MenuCategory();
        category.setRestaurantId(restaurantId);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        return menuCategoryRepository.save(category);
    }

    private MenuItem createMenuItem(java.util.UUID restaurantId,
                                    java.util.UUID categoryId,
                                    String name,
                                    String description,
                                    BigDecimal price) {
        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(restaurantId);
        menuItem.setCategoryId(categoryId);
        menuItem.setName(name);
        menuItem.setDescription(description);
        menuItem.setPrice(price);
        menuItem.setAvailable(true);
        return menuItem;
    }
}
