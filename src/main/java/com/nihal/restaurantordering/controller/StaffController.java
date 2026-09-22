package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.platform.PlatformDtos.*;
import com.nihal.restaurantordering.dto.auth.PasswordResetRequestResponse;
import com.nihal.restaurantordering.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class StaffController {
    private final StaffService staff;
    private final AdminTenantGuard guard;
    private final PlatformService platform;

    @GetMapping("/notice") public SettingsView notice() { return platform.settings(); }
    @GetMapping("/staff") public List<StaffView> list(@AuthenticationPrincipal Jwt jwt) { return staff.list(guard.restaurantId(jwt)); }
    @PostMapping("/staff") public InviteResult invite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteRequest request) { return staff.invite(guard.restaurantId(jwt), request); }
    @PutMapping("/staff/{id}") public StaffView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody StaffUpdate request) { return staff.update(guard.restaurantId(jwt), id, request); }
    @PostMapping("/staff/{id}/revoke") public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { staff.revoke(guard.restaurantId(jwt), id); }
    @PostMapping("/staff/{id}/reset") public PasswordResetRequestResponse reset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return staff.reset(guard.restaurantId(jwt), id); }
}
