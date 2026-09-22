package com.nihal.restaurantordering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.admin.AdminTableResponse;
import com.nihal.restaurantordering.dto.admin.CreateTableRequest;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.customer-base-url=https://order.example.com",
        "spring.datasource.url=jdbc:h2:mem:admin-table-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class AdminTableIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminTableService service;
    @Autowired private JwtService jwtService;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private RestaurantTableRepository tables;
    @Autowired private com.nihal.restaurantordering.repository.RestaurantAdminRepository accounts;
    private UUID restaurantId;
    private Cookie login;

    @BeforeEach
    void setUp() {
        restaurantId = restaurant("QR test restaurant").getId();
        login = loginFor(restaurantId);
    }

    @Test
    void requiresRestaurantLogin() throws Exception {
        mvc.perform(post("/api/admin/tables").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableNumber\":\"T6\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsActiveTableWithPersistedIdAndWorkingCustomerLink() throws Exception {
        var result = mvc.perform(post("/api/admin/tables").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(login).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableNumber\":\"  patio   6  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("PATIO 6"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        var response = mapper.readValue(result.getResponse().getContentAsString(), AdminTableResponse.class);
        assertThat(response.qrCodeUrl()).isEqualTo("https://order.example.com/menu?tableId=" + response.id());
        var stored = tables.findById(response.id()).orElseThrow();
        assertThat(stored.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(stored.getQrCodeUrl()).isEqualTo(response.qrCodeUrl());
        assertThat(stored.getCurrentSessionId()).isNull();
        mvc.perform(get("/api/menu").param("tableId", response.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()));
    }

    @Test
    void duplicateNamesAreRejectedAfterNormalization() throws Exception {
        service.createTable(restaurantId, new CreateTableRequest("T6"));
        mvc.perform(post("/api/admin/tables").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(login).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableNumber\":\" t6 \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A table with this name already exists in your restaurant. Choose another name."));
        assertThat(service.getTables(restaurantId)).hasSize(1);
    }

    @Test
    void invalidNamesAndTenantOverridesAreRejected() throws Exception {
        for (String value : List.of("   ", "X".repeat(31), "<script>alert(1)</script>")) {
            mvc.perform(post("/api/admin/tables").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(login).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("tableNumber", value))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/tables").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(login).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("tableNumber", "T6", "restaurantId", UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        assertThat(service.getTables(restaurantId)).isEmpty();
    }

    @Test
    void restaurantListsAreIsolatedAndNamesMayBeReusedByAnotherRestaurant() throws Exception {
        UUID otherRestaurant = restaurant("Other restaurant").getId();
        var ownTable = service.createTable(restaurantId, new CreateTableRequest("T6"));
        var otherTable = service.createTable(otherRestaurant, new CreateTableRequest("T6"));
        assertThat(otherTable.id()).isNotEqualTo(ownTable.id());
        mvc.perform(get("/api/admin/tables").cookie(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownTable.id().toString()));
        mvc.perform(get("/api/admin/tables").cookie(loginFor(otherRestaurant)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherTable.id().toString()));
    }

    @Test
    void concurrentDuplicateSubmissionsCreateExactlyOneTable() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<String> create = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                service.createTable(restaurantId, new CreateTableRequest("T6"));
                return "created";
            } catch (ConflictException exception) {
                return "duplicate";
            }
        };
        try {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "duplicate");
            assertThat(service.getTables(restaurantId)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseEnforcesUniqueTableNamesWithinRestaurant() {
        service.createTable(restaurantId, new CreateTableRequest("T6"));
        RestaurantTable duplicate = new RestaurantTable();
        duplicate.setRestaurantId(restaurantId);
        duplicate.setTableNumber("T6");
        duplicate.setQrCodeUrl("https://order.example.com/menu");
        assertThatThrownBy(() -> tables.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existingTablesUseTheConfiguredCustomerHost() {
        var response = service.createTable(restaurantId, new CreateTableRequest("T6"));
        RestaurantTable stored = tables.findById(response.id()).orElseThrow();
        stored.setQrCodeUrl("https://yourapp.com/menu?tableId=" + stored.getId());
        tables.saveAndFlush(stored);
        assertThat(service.getTables(restaurantId).get(0).qrCodeUrl()).isEqualTo(response.qrCodeUrl());
    }

    private Restaurant restaurant(String name) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setLocation("Test");
        return restaurants.saveAndFlush(restaurant);
    }

    private Cookie loginFor(UUID tenantId) {
        RestaurantAdmin admin = new RestaurantAdmin();
        admin.setRestaurantId(tenantId);
        admin.setUsername("test-" + UUID.randomUUID());
        admin.setUsernameNormalized(admin.getUsername());
        admin.setEmail(admin.getUsername() + "@example.com");
        admin.setEmailNormalized(admin.getEmail());
        admin.setPasswordHash("unused-by-token-tests");
        accounts.saveAndFlush(admin);
        return new Cookie("restaurant_access_token", jwtService.createAccessToken(admin));
    }
}
