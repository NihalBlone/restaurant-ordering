package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.menu.MenuImageResponse;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import com.nihal.restaurantordering.service.MenuImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants/{restaurantId}/menu-images")
public class MenuImageController {

    private final MenuImageStorageService menuImageStorageService;
    private final AdminTenantGuard adminTenantGuard;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MenuImageResponse uploadMenuImage(
            @PathVariable UUID restaurantId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        adminTenantGuard.requireRestaurant(jwt, restaurantId);
        return new MenuImageResponse(menuImageStorageService.store(file));
    }
}
