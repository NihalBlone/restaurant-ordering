package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.report.SalesReportResponse;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import com.nihal.restaurantordering.service.SalesReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
public class SalesReportController {

    private final SalesReportService salesReportService;
    private final AdminTenantGuard adminTenantGuard;

    @GetMapping("/sales")
    public SalesReportResponse getSalesReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID tableId,
            @RequestParam(required = false) UUID menuItemId,
            @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return salesReportService.getSalesReport(
                adminTenantGuard.restaurantId(jwt),
                from,
                to,
                tableId,
                menuItemId,
                timeZone,
                page,
                size
        );
    }
}
