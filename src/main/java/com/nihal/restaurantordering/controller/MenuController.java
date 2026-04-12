package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.menu.MenuResponse;
import com.nihal.restaurantordering.service.MenuService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public MenuResponse getMenu(@RequestParam @NotNull UUID tableId) {
        return menuService.getMenuByTable(tableId);
    }
}
