package com.nihal.restaurantordering.bootstrap;

import com.nihal.restaurantordering.domain.MenuCategory;
import com.nihal.restaurantordering.domain.MenuItem;
import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.dto.admin.CreateTableRequest;
import com.nihal.restaurantordering.service.AdminTableService;
import com.nihal.restaurantordering.repository.MenuCategoryRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.sample.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SampleDataLoader implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantAdminRepository restaurantAdminRepository;
    private final AdminTableService adminTableService;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.sample.admin-username}")
    private String sampleAdminUsername;

    @Value("${app.sample.admin-email}")
    private String sampleAdminEmail;

    @Value("${app.sample.admin-password}")
    private String sampleAdminPassword;

    @Override
    public void run(String... args) {
        if (restaurantRepository.count() > 0) {
            restaurantRepository.findAll().stream().findFirst().ifPresent(this::ensureSampleAdmin);
            return;
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName("Spice Garden");
        restaurant.setLocation("Bengaluru, India");
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        ensureSampleAdmin(savedRestaurant);

        for (int i = 1; i <= 5; i++) {
            var table = adminTableService.createTable(savedRestaurant.getId(), new CreateTableRequest("T" + i));
            log.info("Sample table created. tableNumber={} tableId={}", table.tableNumber(), table.id());
        }

        log.info("Sample restaurant created. restaurantId={} name={}", savedRestaurant.getId(), savedRestaurant.getName());

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

    private void ensureSampleAdmin(Restaurant restaurant) {
        String normalizedUsername = sampleAdminUsername.trim().toLowerCase(Locale.ROOT);
        if (restaurantAdminRepository.existsByUsernameNormalized(normalizedUsername)) {
            return;
        }

        RestaurantAdmin admin = new RestaurantAdmin();
        admin.setRestaurantId(restaurant.getId());
        admin.setUsername(sampleAdminUsername.trim());
        admin.setUsernameNormalized(normalizedUsername);
        admin.setEmail(sampleAdminEmail.trim());
        admin.setEmailNormalized(sampleAdminEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(sampleAdminPassword));
        admin.setActive(true);
        restaurantAdminRepository.save(admin);
        log.info("Sample restaurant admin created. username={} restaurantId={}",
                sampleAdminUsername, restaurant.getId());
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
        menuItem.setVegetarian(true);
        menuItem.setAvailable(true);
        return menuItem;
    }
}
