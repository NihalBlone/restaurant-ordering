package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.dto.platform.PlatformDtos.*;
import com.nihal.restaurantordering.exception.*;
import com.nihal.restaurantordering.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformService {
    private final RestaurantRepository restaurants;
    private final RestaurantTableRepository tables;
    private final RestaurantAdminRepository accounts;
    private final PlatformSettingsRepository settings;
    private final AuditRepository audits;
    private final StaffService staff;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly=true)
    public Map<String, Object> overview() {
        return Map.of("restaurants", restaurants.count(),
                "activeRestaurants", restaurants.countByStatus(RestaurantStatus.ACTIVE),
                "suspendedRestaurants", restaurants.countByStatus(RestaurantStatus.SUSPENDED),
                "staffAccounts", accounts.count(),
                "orders", jdbc.queryForObject("select count(*) from orders", Long.class),
                "settledBills", jdbc.queryForObject("select count(*) from dining_sessions where status = 'SETTLED'", Long.class),
                "settledSales", jdbc.queryForObject("select coalesce(sum(total_amount), 0) from dining_sessions where status = 'SETTLED'", java.math.BigDecimal.class));
    }

    @Transactional(readOnly=true)
    public PageView<RestaurantView> list(String search, int page) {
        return PageView.of(restaurants.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), PageRequest.of(page, 20)).map(this::view));
    }

    @Transactional
    public OnboardResult onboard(OnboardRequest request) {
        if (request.owner().role() != AdminRole.RESTAURANT_ADMIN) throw new BadRequestException("The initial account must be a restaurant owner");
        var restaurant = new Restaurant();
        apply(restaurant, request.restaurant());
        restaurants.saveAndFlush(restaurant);
        var invitation = staff.invite(restaurant.getId(), request.owner());
        audit.record("RESTAURANT_CREATED", restaurant.getId(), restaurant.getId(), "Plan: " + restaurant.getPlanCode());
        return new OnboardResult(view(restaurant), invitation);
    }

    @Transactional
    public RestaurantView update(UUID id, RestaurantInput request) {
        var restaurant = restaurants.findForUpdate(id).orElseThrow(() -> new NotFoundException("Restaurant not found"));
        if (request.tableLimit() < tables.countByRestaurantId(id) || request.staffLimit() < accounts.countByRestaurantId(id)) {
            throw new ConflictException("Limits cannot be lower than the number of existing tables or accounts");
        }
        apply(restaurant, request);
        audit.record("RESTAURANT_UPDATED", id, id, "Plan: " + request.planCode() + "; tables: " + request.tableLimit() + "; staff: " + request.staffLimit());
        return view(restaurant);
    }

    @Transactional
    public RestaurantView status(UUID id, StatusUpdate request) {
        var restaurant = restaurants.findForUpdate(id).orElseThrow(() -> new NotFoundException("Restaurant not found"));
        restaurant.setStatus(request.status());
        // Reactivating a restaurant must not revive old signed-in sessions.
        accounts.revokeRestaurantSessions(id);
        audit.record("RESTAURANT_" + request.status(), id, id, request.reason().trim());
        return view(restaurant);
    }

    @Transactional(readOnly=true)
    public SettingsView settings() { return settingsView(settings.findById(1).orElseThrow()); }

    @Transactional
    public SettingsView updateSettings(SettingsInput request) {
        var current = settings.findById(1).orElseThrow();
        if (!Objects.equals(current.getVersion(), request.version())) throw new ConflictException("Settings changed elsewhere. Refresh before saving.");
        current.setProductName(request.productName().trim());
        current.setSupportEmail(request.supportEmail() == null ? "" : request.supportEmail().trim());
        current.setAnnouncement(request.announcement().trim());
        current.setUpdatedAt(OffsetDateTime.now());
        settings.flush();
        audit.record("PLATFORM_SETTINGS_UPDATED", null, null, "Product settings and announcement updated");
        return settingsView(current);
    }

    @Transactional(readOnly=true)
    public PageView<AuditView> audit(UUID restaurantId, int page) {
        return PageView.of(audits.search(restaurantId, PageRequest.of(page, 30)).map(a ->
                new AuditView(a.getId(), a.getActorName(), a.getRestaurantId(), a.getAction(), a.getSubjectId(), a.getDetails(), a.getCreatedAt())));
    }

    public Map<String, Object> health() {
        jdbc.queryForObject("select 1", Integer.class);
        return Map.of("database", "UP", "uptimeSeconds", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "backups", "Configure and verify backups with your database hosting provider", "messaging", "Single-instance STOMP broker");
    }

    private void apply(Restaurant restaurant, RestaurantInput request) {
        restaurant.setName(request.name().trim());
        restaurant.setLocation(request.location().trim());
        restaurant.setPlanCode(request.planCode());
        restaurant.setTableLimit(request.tableLimit());
        restaurant.setStaffLimit(request.staffLimit());
        restaurant.setTrialEndsAt(request.trialEndsAt());
    }

    private RestaurantView view(Restaurant r) {
        return new RestaurantView(r.getId(), r.getName(), r.getLocation(), r.getStatus(), r.getPlanCode(),
                r.getTableLimit(), r.getStaffLimit(), tables.countByRestaurantId(r.getId()), accounts.countByRestaurantId(r.getId()), r.getTrialEndsAt(), r.getCreatedAt());
    }
    private SettingsView settingsView(PlatformSettings s) {
        return new SettingsView(s.getProductName(), s.getSupportEmail(), s.getAnnouncement(), s.getUpdatedAt(), s.getVersion());
    }
}
