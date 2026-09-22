package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.menu.AdminMenuCategoryResponse;
import com.nihal.restaurantordering.dto.menu.CreateMenuCategoryRequest;
import com.nihal.restaurantordering.dto.menu.UpdateMenuCategoryRequest;
import com.nihal.restaurantordering.service.MenuManagementService;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants/{restaurantId}/menu-categories")
public class MenuCategoryManagementController {

    private final MenuManagementService menuManagementService;
    private final AdminTenantGuard adminTenantGuard;

    @GetMapping
    public List<AdminMenuCategoryResponse> getMenuCategories(@PathVariable UUID restaurantId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.getMenuCategories(restaurantId);
    }

    @PostMapping
    public AdminMenuCategoryResponse createMenuCategory(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMenuCategoryRequest request
    ) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.createMenuCategory(restaurantId, request);
    }

    @PutMapping("/{categoryId}")
    public AdminMenuCategoryResponse updateMenuCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMenuCategoryRequest request
    ) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.updateMenuCategory(restaurantId, categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenuCategory(@PathVariable UUID restaurantId,
                                   @PathVariable UUID categoryId,
                                   @AuthenticationPrincipal Jwt jwt) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        menuManagementService.deleteMenuCategory(restaurantId, categoryId);
    }
}
