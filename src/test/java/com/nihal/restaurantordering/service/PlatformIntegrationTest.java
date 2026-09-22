package com.nihal.restaurantordering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.dto.admin.CreateTableRequest;
import com.nihal.restaurantordering.dto.platform.PlatformDtos.*;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:platform-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.sample.enabled=false"})
@AutoConfigureMockMvc
class PlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantAdminRepository accounts;
    @Autowired RestaurantTableRepository tables;
    @Autowired JwtService jwt;
    @Autowired PlatformService platform;
    @Autowired StaffService staff;
    @Autowired AuthService auth;
    @Autowired AdminTableService tableService;
    @Autowired PasswordEncoder encoder;
    private Restaurant restaurant;
    private RestaurantAdmin owner;
    private RestaurantAdmin platformOwner;

    @BeforeEach void seed() {
        restaurant = new Restaurant(); restaurant.setName("Platform test"); restaurant.setLocation("Test");
        restaurants.saveAndFlush(restaurant);
        owner = account(restaurant.getId(), AdminRole.RESTAURANT_ADMIN);
        platformOwner = account(null, AdminRole.PLATFORM_ADMIN);
    }

    @Test void separatesPlatformAndRestaurantAccessAndRequiresCsrf() throws Exception {
        mvc.perform(get("/api/platform/overview").cookie(cookie(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/overview").cookie(cookie(platformOwner))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/tables").cookie(cookie(platformOwner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/tables").cookie(cookie(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"tableNumber\":\"T1\"}"))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> auth.login(platformOwner.getUsername(), "Password-for-tests!1"))
                .hasMessage("Invalid credentials for this portal");
        assertThat(auth.platformLogin(platformOwner.getUsername(), "Password-for-tests!1", "").session().role()).isEqualTo("PLATFORM_ADMIN");
    }

    @Test void onboardsRestaurantWithOwnerInvitationAndAudits() throws Exception {
        var request = new OnboardRequest(new RestaurantInput("New cafe", "Town", "STARTER", 5, 3, null), invite(AdminRole.RESTAURANT_ADMIN));
        mvc.perform(post("/api/platform/restaurants").with(csrf()).cookie(cookie(platformOwner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restaurant.name").value("New cafe"))
                .andExpect(jsonPath("$.invitation.developmentResetToken").isString());
        mvc.perform(get("/api/platform/audit").cookie(cookie(platformOwner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorName").value(platformOwner.getUsername()));
    }

    @Test void suspensionBlocksExistingTokensAndCustomerQrAndReactivationDoesNotReviveTokens() throws Exception {
        Cookie previous = cookie(owner);
        var table = tableService.createTable(restaurant.getId(), new CreateTableRequest("T1"));
        platform.status(restaurant.getId(), new StatusUpdate(RestaurantStatus.SUSPENDED, "Test suspension"));
        mvc.perform(get("/api/admin/tables").cookie(previous)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menu").param("tableId", table.id().toString())).andExpect(status().isForbidden());
        platform.status(restaurant.getId(), new StatusUpdate(RestaurantStatus.ACTIVE, "Resolved"));
        mvc.perform(get("/api/admin/tables").cookie(previous)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menu").param("tableId", table.id().toString())).andExpect(status().isOk());
    }

    @Test void enforcesStaffRolesTenantBoundariesAndLastOwner() throws Exception {
        var kitchen = account(restaurant.getId(), AdminRole.KITCHEN);
        mvc.perform(get("/api/admin/staff").cookie(cookie(kitchen))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/sales").cookie(cookie(kitchen))).andExpect(status().isForbidden());
        mvc.perform(post("/api/table-sessions/" + UUID.randomUUID() + "/close").with(csrf()).cookie(cookie(kitchen))).andExpect(status().isForbidden());
        mvc.perform(get("/api/restaurants/" + UUID.randomUUID() + "/menu-items").cookie(cookie(owner))).andExpect(status().isForbidden());
        assertThatThrownBy(() -> staff.update(restaurant.getId(), owner.getId(), new StaffUpdate(AdminRole.WAITER, true))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> staff.invite(restaurant.getId(), invite(AdminRole.PLATFORM_ADMIN))).hasMessageContaining("Platform privileges");
        var invited = staff.invite(restaurant.getId(), invite(AdminRole.MANAGER));
        assertThat(invited.account().role()).isEqualTo(AdminRole.MANAGER);
        assertThatThrownBy(() -> staff.revoke(UUID.randomUUID(), invited.account().id())).hasMessageContaining("not found");
    }

    @Test void passwordResetIsSingleUseAndRevokesTokens() throws Exception {
        Cookie previous = cookie(owner);
        var reset = auth.requestPasswordReset(owner.getUsername());
        auth.resetPassword(reset.developmentResetToken(), "Updated-password!2");
        mvc.perform(get("/api/auth/me").cookie(previous)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.resetPassword(reset.developmentResetToken(), "Updated-password!2")).hasMessageContaining("invalid or expired");
    }

    @Test void rejectsPasswordsLongerThanBcryptByteLimitWithoutServerError() throws Exception {
        String oversized = "Aa1!" + "\u00e9".repeat(40);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", owner.getUsername(), "password", oversized))))
                .andExpect(status().isUnauthorized());
        var reset = auth.requestPasswordReset(owner.getUsername());
        mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("token", reset.developmentResetToken(), "newPassword", oversized))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        "Password must be at most 72 UTF-8 bytes; use fewer characters"));
        auth.resetPassword(reset.developmentResetToken(), "Valid-password!123");
    }

    @Test void revokedAndDisabledAccountsCannotUseExistingCookies() throws Exception {
        var waiter = account(restaurant.getId(), AdminRole.WAITER);
        Cookie previous = cookie(waiter);
        staff.revoke(restaurant.getId(), waiter.getId());
        mvc.perform(get("/api/admin/tables").cookie(previous)).andExpect(status().isUnauthorized());
        waiter = accounts.findById(waiter.getId()).orElseThrow();
        Cookie fresh = cookie(waiter);
        staff.update(restaurant.getId(), waiter.getId(), new StaffUpdate(AdminRole.WAITER, false));
        mvc.perform(get("/api/admin/tables").cookie(fresh)).andExpect(status().isUnauthorized());
    }

    @Test void concurrentTableCreationCannotExceedLimit() throws Exception {
        restaurant.setTableLimit(1); restaurants.saveAndFlush(restaurant);
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        Callable<Boolean> operation = () -> { gate.await(); try {
            tableService.createTable(restaurant.getId(), new CreateTableRequest(UUID.randomUUID().toString().substring(0,8)));
            return true;
        } catch (ConflictException expected) { return false; } };
        try {
            var first = pool.submit(operation); var second = pool.submit(operation); gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(tables.countByRestaurantId(restaurant.getId())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void protectsSettingsFromLostUpdates() {
        var initial = platform.settings();
        var input = new SettingsInput("Tableside test", "help@example.com", "Notice", initial.version());
        platform.updateSettings(input);
        assertThatThrownBy(() -> platform.updateSettings(input)).isInstanceOf(ConflictException.class);
    }

    @Test void concurrentStaffInvitesCannotExceedLimit() throws Exception {
        restaurant.setStaffLimit(2); restaurants.saveAndFlush(restaurant);
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        Callable<Boolean> operation = () -> { gate.await(); try {
            staff.invite(restaurant.getId(), invite(AdminRole.WAITER)); return true;
        } catch (ConflictException expected) { return false; } };
        try {
            var first = pool.submit(operation); var second = pool.submit(operation); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(accounts.countByRestaurantId(restaurant.getId())).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentPasswordResetsConsumeTokenExactlyOnce() throws Exception {
        var token = auth.requestPasswordReset(owner.getUsername()).developmentResetToken();
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        Callable<Boolean> operation = () -> { gate.await(); try {
            auth.resetPassword(token, "Concurrent-password!3"); return true;
        } catch (com.nihal.restaurantordering.exception.BadRequestException expected) { return false; } };
        try {
            var first = pool.submit(operation); var second = pool.submit(operation); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
    }

    private InviteRequest invite(AdminRole role) {
        String username = "u" + UUID.randomUUID();
        return new InviteRequest(username, username + "@example.com", role);
    }

    private RestaurantAdmin account(UUID restaurantId, AdminRole role) {
        var value = new RestaurantAdmin(); value.setRestaurantId(restaurantId); value.setRole(role);
        value.setUsername("u" + UUID.randomUUID()); value.setUsernameNormalized(value.getUsername());
        value.setEmail(value.getUsername() + "@example.com"); value.setEmailNormalized(value.getEmail());
        value.setPasswordHash(encoder.encode("Password-for-tests!1"));
        return accounts.saveAndFlush(value);
    }
    private Cookie cookie(RestaurantAdmin account) { return new Cookie("restaurant_access_token", jwt.createAccessToken(account)); }
}
