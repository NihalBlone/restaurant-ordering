package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.admin.AdminTableResponse;
import com.nihal.restaurantordering.dto.admin.CreateTableRequest;
import com.nihal.restaurantordering.service.AdminTableService;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tables")
public class AdminTableController {

    private final AdminTableService adminTableService;
    private final AdminTenantGuard adminTenantGuard;

    @GetMapping
    public List<AdminTableResponse> getTables(@AuthenticationPrincipal Jwt jwt) {
        return adminTableService.getTables(adminTenantGuard.restaurantId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTableResponse createTable(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody CreateTableRequest request) {
        return adminTableService.createTable(adminTenantGuard.restaurantId(jwt), request);
    }
}
