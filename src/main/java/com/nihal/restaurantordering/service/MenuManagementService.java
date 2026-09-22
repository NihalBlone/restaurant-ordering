package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.MenuCategory;
import com.nihal.restaurantordering.domain.MenuItem;
import com.nihal.restaurantordering.dto.menu.AdminMenuCategoryResponse;
import com.nihal.restaurantordering.dto.menu.AdminMenuItemResponse;
import com.nihal.restaurantordering.dto.menu.CreateMenuCategoryRequest;
import com.nihal.restaurantordering.dto.menu.CreateMenuItemRequest;
import com.nihal.restaurantordering.dto.menu.UpdateMenuCategoryRequest;
import com.nihal.restaurantordering.dto.menu.UpdateMenuItemRequest;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.MenuCategoryRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuManagementService {

    private final RestaurantContextService restaurantContextService;
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    @Transactional(readOnly = true)
    public List<AdminMenuItemResponse> getMenuItems(UUID restaurantId) {
        restaurantContextService.getRestaurant(restaurantId);
        return menuItemRepository.findAdminMenuItemsByRestaurantId(restaurantId)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminMenuCategoryResponse> getMenuCategories(UUID restaurantId) {
        restaurantContextService.getRestaurant(restaurantId);
        return menuCategoryRepository.findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(restaurantId)
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "menuByTable", allEntries = true)
    public AdminMenuCategoryResponse createMenuCategory(UUID restaurantId, CreateMenuCategoryRequest request) {
        restaurantContextService.getRestaurant(restaurantId);
        String name = normalizeRequiredText(request.name(), "name");
        if (menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ConflictException("A menu category named '" + name + "' already exists");
        }

        MenuCategory category = new MenuCategory();
        category.setRestaurantId(restaurantId);
        category.setName(name);
        category.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder()
                : menuCategoryRepository.findMaxDisplayOrder(restaurantId) + 1);
        MenuCategory savedCategory = menuCategoryRepository.save(category);
        log.info("Menu category {} created for restaurant {}", savedCategory.getId(), restaurantId);
        return toCategoryResponse(savedCategory);
    }

    @Transactional
    @CacheEvict(cacheNames = "menuByTable", allEntries = true)
    public AdminMenuCategoryResponse updateMenuCategory(UUID restaurantId,
                                                        UUID categoryId,
                                                        UpdateMenuCategoryRequest request) {
        restaurantContextService.getRestaurant(restaurantId);
        MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu category not found for this restaurant"));
        String name = normalizeRequiredText(request.name(), "name");
        if (menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(
                restaurantId, name, categoryId)) {
            throw new ConflictException("A menu category named '" + name + "' already exists");
        }
        category.setName(name);
        category.setDisplayOrder(request.displayOrder());
        MenuCategory savedCategory = menuCategoryRepository.save(category);
        log.info("Menu category {} updated for restaurant {}", categoryId, restaurantId);
        return toCategoryResponse(savedCategory);
    }

    @Transactional
    @CacheEvict(cacheNames = "menuByTable", allEntries = true)
    public void deleteMenuCategory(UUID restaurantId, UUID categoryId) {
        restaurantContextService.getRestaurant(restaurantId);
        MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu category not found for this restaurant"));
        if (menuItemRepository.countByCategoryIdAndRestaurantId(categoryId, restaurantId) > 0) {
            throw new ConflictException("Move or remove all menu items before deleting this category");
        }
        menuCategoryRepository.delete(category);
        log.info("Menu category {} deleted for restaurant {}", categoryId, restaurantId);
    }

    @Transactional
    @CacheEvict(cacheNames = "menuByTable", allEntries = true)
    public AdminMenuItemResponse createMenuItem(UUID restaurantId, CreateMenuItemRequest request) {
        restaurantContextService.getRestaurant(restaurantId);
        MenuCategory category = menuCategoryRepository
                .findByIdAndRestaurantId(request.categoryId(), restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu category not found for this restaurant"));

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurantId(restaurantId);
        menuItem.setCategoryId(category.getId());
        menuItem.setName(normalizeRequiredText(request.name(), "name"));
        menuItem.setDescription(normalizeRequiredText(request.description(), "description"));
        menuItem.setPrice(request.price());
        menuItem.setImageUrl(normalizeOptionalText(request.imageUrl()));
        menuItem.setVegetarian(request.vegetarian());
        menuItem.setAvailable(request.available());
        MenuItem savedItem = menuItemRepository.save(menuItem);
        log.info("Menu item {} created for restaurant {} category {}",
                savedItem.getId(), restaurantId, category.getId());
        return toAdminResponse(savedItem, category);
    }

    @Transactional
    @CacheEvict(cacheNames = "menuByTable", allEntries = true)
    public AdminMenuItemResponse updateMenuItem(UUID restaurantId,
                                                UUID menuItemId,
                                                UpdateMenuItemRequest request) {
        restaurantContextService.getRestaurant(restaurantId);
        MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu item not found for this restaurant"));
        MenuCategory category = menuCategoryRepository
                .findByIdAndRestaurantId(menuItem.getCategoryId(), restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu category not found for this restaurant"));

        menuItem.setName(normalizeRequiredText(request.name(), "name"));
        menuItem.setDescription(normalizeRequiredText(request.description(), "description"));
        menuItem.setPrice(request.price());
        menuItem.setImageUrl(normalizeOptionalText(request.imageUrl()));
        menuItem.setVegetarian(request.vegetarian());
        menuItem.setAvailable(request.available());

        MenuItem savedItem = menuItemRepository.save(menuItem);
        log.info("Menu item {} updated for restaurant {}", savedItem.getId(), restaurantId);
        return toAdminResponse(savedItem, category);
    }

    private AdminMenuItemResponse toAdminResponse(MenuItemRepository.AdminMenuItemProjection row) {
        return AdminMenuItemResponse.builder()
                .id(row.getItemId())
                .categoryId(row.getCategoryId())
                .categoryName(row.getCategoryName())
                .categoryDisplayOrder(row.getCategoryDisplayOrder())
                .name(row.getItemName())
                .description(row.getItemDescription())
                .price(row.getItemPrice())
                .imageUrl(row.getItemImageUrl())
                .vegetarian(Boolean.TRUE.equals(row.getItemVegetarian()))
                .available(Boolean.TRUE.equals(row.getItemAvailable()))
                .build();
    }

    private AdminMenuItemResponse toAdminResponse(MenuItem item, MenuCategory category) {
        return AdminMenuItemResponse.builder()
                .id(item.getId())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .categoryDisplayOrder(category.getDisplayOrder())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .imageUrl(item.getImageUrl())
                .vegetarian(item.isVegetarian())
                .available(item.isAvailable())
                .build();
    }

    private AdminMenuCategoryResponse toCategoryResponse(MenuCategory category) {
        return AdminMenuCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .build();
    }

    private String normalizeRequiredText(String input, String fieldName) {
        String normalized = input.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalText(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
