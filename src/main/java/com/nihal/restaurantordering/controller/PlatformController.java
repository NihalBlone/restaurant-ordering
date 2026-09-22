package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.platform.PlatformDtos.*;
import com.nihal.restaurantordering.dto.admin.AdminTableResponse;
import com.nihal.restaurantordering.dto.auth.PasswordResetRequestResponse;
import com.nihal.restaurantordering.dto.menu.AdminMenuItemResponse;
import com.nihal.restaurantordering.dto.report.SalesReportResponse;
import com.nihal.restaurantordering.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Validated
public class PlatformController {
    private final PlatformService platform;
    private final StaffService staff;
    private final AdminTableService tables;
    private final MenuManagementService menu;
    private final SalesReportService sales;
    private final AuditService audit;

    @GetMapping("/overview") public Map<String, Object> overview() { return platform.overview(); }
    @GetMapping("/restaurants") public PageView<RestaurantView> list(
            @RequestParam(defaultValue="") @Size(max=120) String search, @RequestParam(defaultValue="0") @Min(0) int page) {
        return platform.list(search, page);
    }
    @PostMapping("/restaurants") public OnboardResult onboard(@Valid @RequestBody OnboardRequest request) { return platform.onboard(request); }
    @PutMapping("/restaurants/{id}") public RestaurantView update(@PathVariable UUID id, @Valid @RequestBody RestaurantInput request) { return platform.update(id, request); }
    @PutMapping("/restaurants/{id}/status") public RestaurantView status(@PathVariable UUID id, @Valid @RequestBody StatusUpdate request) { return platform.status(id, request); }
    @GetMapping("/restaurants/{id}/staff") public List<StaffView> staff(@PathVariable UUID id) { return staff.list(id); }
    @PostMapping("/restaurants/{id}/staff") public InviteResult invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest request) { return staff.invite(id, request); }
    @PutMapping("/restaurants/{id}/staff/{accountId}") public StaffView updateStaff(@PathVariable UUID id, @PathVariable UUID accountId, @Valid @RequestBody StaffUpdate request) { return staff.update(id, accountId, request); }
    @PostMapping("/restaurants/{id}/staff/{accountId}/revoke") public void revoke(@PathVariable UUID id, @PathVariable UUID accountId) { staff.revoke(id, accountId); }
    @PostMapping("/restaurants/{id}/staff/{accountId}/reset") public PasswordResetRequestResponse reset(@PathVariable UUID id, @PathVariable UUID accountId) { return staff.reset(id, accountId); }
    @GetMapping("/restaurants/{id}/tables") public List<AdminTableResponse> tables(@PathVariable UUID id) { return tables.getTables(id); }
    @GetMapping("/restaurants/{id}/menu-items") public List<AdminMenuItemResponse> menu(@PathVariable UUID id) { return menu.getMenuItems(id); }
    @GetMapping("/restaurants/{id}/reports/sales") public SalesReportResponse sales(
            @PathVariable UUID id, @RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to,
            @RequestParam(required=false) UUID tableId, @RequestParam(required=false) UUID menuItemId,
            @RequestParam(defaultValue="UTC") String timeZone,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="25") @Min(1) @Max(100) int size) {
        var report = sales.getSalesReport(id, from, to, tableId, menuItemId, timeZone, page, size);
        audit.record("SALES_REPORT_VIEWED", id, id, "Range: " + from + " to " + to);
        return report;
    }
    @GetMapping("/audit") public PageView<AuditView> audit(@RequestParam(required=false) UUID restaurantId, @RequestParam(defaultValue="0") @Min(0) int page) { return platform.audit(restaurantId, page); }
    @GetMapping("/settings") public SettingsView settings() { return platform.settings(); }
    @PutMapping("/settings") public SettingsView settings(@Valid @RequestBody SettingsInput request) { return platform.updateSettings(request); }
    @GetMapping("/health") public Map<String, Object> health() { return platform.health(); }
}
