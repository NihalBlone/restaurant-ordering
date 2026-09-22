package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.session.TableSessionCloseResponse;
import com.nihal.restaurantordering.service.TableSessionService;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/table-sessions")
public class TableSessionController {

    private final TableSessionService tableSessionService;
    private final AdminTenantGuard adminTenantGuard;

    @PostMapping("/{tableId}/close")
    public TableSessionCloseResponse closeCurrentSession(
            @PathVariable UUID tableId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return tableSessionService.closeCurrentSession(
                tableId,
                adminTenantGuard.restaurantId(jwt)
        );
    }
}
