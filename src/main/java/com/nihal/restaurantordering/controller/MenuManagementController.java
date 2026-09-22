package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.menu.AdminMenuItemResponse;
import com.nihal.restaurantordering.dto.menu.CreateMenuItemRequest;
import com.nihal.restaurantordering.dto.menu.UpdateMenuItemRequest;
import com.nihal.restaurantordering.service.MenuManagementService;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants/{restaurantId}/menu-items")
public class MenuManagementController {

    private final MenuManagementService menuManagementService;
    private final AdminTenantGuard adminTenantGuard;

    @GetMapping
    public List<AdminMenuItemResponse> getMenuItems(@PathVariable UUID restaurantId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.getMenuItems(restaurantId);
    }

    @PostMapping
    public AdminMenuItemResponse createMenuItem(@PathVariable UUID restaurantId,
                                                @AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateMenuItemRequest request) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.createMenuItem(restaurantId, request);
    }

    @PutMapping("/{menuItemId}")
    public AdminMenuItemResponse updateMenuItem(@PathVariable UUID restaurantId,
                                                @PathVariable UUID menuItemId,
                                                @AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody UpdateMenuItemRequest request) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return menuManagementService.updateMenuItem(restaurantId, menuItemId, request);
    }
}
