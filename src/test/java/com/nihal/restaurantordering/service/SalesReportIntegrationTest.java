package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.dto.report.SalesReportResponse;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sales-report-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class SalesReportIntegrationTest {
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-09-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-09-03T23:59:59.999Z");
    @Autowired private SalesReportService reports;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private RestaurantTableRepository tables;
    @Autowired private MenuCategoryRepository categories;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private DiningSessionRepository sessions;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private OrderItemRepository items;
    @Autowired private JwtService jwtService;
    @Autowired private RestaurantAdminRepository accounts;
    @Autowired private MockMvc mvc;
    private UUID tenant;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable foreignTable;
    private MenuItem tea;
    private MenuItem cake;
    private MenuItem unsold;
    private MenuItem foreignItem;

    @BeforeEach
    void seedHistory() {
        tenant = restaurant();
        table1 = table(tenant, "T1");
        table2 = table(tenant, "T2");
        tea = menuItem(tenant, "Tea");
        tea.setAvailable(false);
        menuItems.saveAndFlush(tea);
        cake = menuItem(tenant, "Cake");
        unsold = menuItem(tenant, "Coffee");
        DiningSession first = session(table1, "2026-09-01T20:00:00Z");
        order(first, new Sale(tea, 2, "10.10"), new Sale(cake, 1, "50.00"));
        order(first, new Sale(tea, 1, "12.30"));
        order(session(table2, "2026-09-02T10:00:00Z"), new Sale(tea, 3, "10.00"));
        order(session(table1, "2026-09-03T10:00:00Z"), new Sale(cake, 2, "50.00"));
        order(session(table1, "2026-08-31T23:59:59Z"), new Sale(tea, 9, "10.00"));
        order(session(table1, null), new Sale(tea, 4, "10.00"));
        UUID other = restaurant();
        foreignTable = table(other, "T1");
        foreignItem = menuItem(other, "Tea");
        order(session(foreignTable, "2026-09-02T10:00:00Z"), new Sale(foreignItem, 99, "100.00"));
    }

    @Test
    void aggregatesAllMatchingPagesWithoutDoubleCountingMixedOrders() {
        SalesReportResponse report = report(null, null, 0, "UTC");
        assertThat(report.settledBills()).isEqualTo(3);
        assertThat(report.totalOrders()).isEqualTo(4);
        assertThat(report.totalItems()).isEqualTo(9);
        assertThat(report.totalAmount()).isEqualByComparingTo("212.50");
        assertThat(report.statements()).hasSize(1);
        assertThat(report.totalPages()).isEqualTo(3);
        assertThat(report.items()).hasSize(2).anySatisfy(item -> {
            assertThat(item.menuItemId()).isEqualTo(tea.getId());
            assertThat(item.totalItems()).isEqualTo(6);
            assertThat(item.totalAmount()).isEqualByComparingTo("62.50");
        });
        assertThat(report.tables().stream().map(table -> table.totalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(report.totalAmount());
        assertThat(report.items().stream().map(item -> item.totalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(report.totalAmount());
        assertThat(report(null, null, 1, "UTC").items()).isEqualTo(report.items());
    }

    @Test
    void filtersByFoodAcrossTablesIncludingUnavailableAndRenamedItems() {
        SalesReportResponse report = report(null, tea.getId(), 1, "UTC");
        assertThat(report.settledBills()).isEqualTo(2);
        assertThat(report.totalElements()).isEqualTo(2);
        assertThat(report.totalOrders()).isEqualTo(3);
        assertThat(report.totalItems()).isEqualTo(6);
        assertThat(report.totalAmount()).isEqualByComparingTo("62.50");
        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).name()).isEqualTo("Tea");
        var statement = report.statements().get(0);
        assertThat(statement.totalAmount()).isEqualByComparingTo("32.50");
        assertThat(statement.billTotalAmount()).isEqualByComparingTo("82.50");
        assertThat(statement.orders()).hasSize(2).allSatisfy(order ->
                assertThat(order.items()).allSatisfy(item -> {
                    assertThat(item.menuItemId()).isEqualTo(tea.getId());
                    assertThat(item.menuItemName()).isEqualTo("Original Tea");
                }));
    }

    @Test
    void combinesTableFoodAndDateFilters() {
        SalesReportResponse report = report(table1.getId(), tea.getId(), 0, "UTC");
        assertThat(report.totalAmount()).isEqualByComparingTo("32.50");
        assertThat(report.totalItems()).isEqualTo(3);
        assertThat(report.totalOrders()).isEqualTo(2);
        assertThat(report.tables()).hasSize(1);
        assertThat(report.days()).hasSize(1);
        assertThat(report.statements()).hasSize(1);
    }

    @Test
    void groupsBySettlementDayInRequestedTimeZone() {
        SalesReportResponse report = report(null, null, 0, "Asia/Kolkata");
        assertThat(report.days()).hasSize(2);
        assertThat(report.days().get(0).date().toString()).isEqualTo("2026-09-02");
        assertThat(report.days().get(0).totalAmount()).isEqualByComparingTo("112.50");
        assertThat(report.days().get(0).totalItems()).isEqualTo(7);
        assertThat(report(null, null, 0, "UTC").days()).hasSize(3);
    }

    @Test
    void emptyResultsHaveEmptyChartsAndZeroTotals() {
        SalesReportResponse report = report(null, unsold.getId(), 0, "UTC");
        assertThat(report.totalAmount()).isEqualByComparingTo("0");
        assertThat(report.totalElements()).isZero();
        assertThat(report.items()).isEmpty();
        assertThat(report.tables()).isEmpty();
        assertThat(report.days()).isEmpty();
        assertThat(report.statements()).isEmpty();
    }

    @Test
    void foreignFiltersAndInvalidRangesAreRejected() {
        assertThatThrownBy(() -> report(foreignTable.getId(), null, 0, "UTC")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> report(null, foreignItem.getId(), 0, "UTC")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> report(null, null, 0, "Not/AZone")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reports.getSalesReport(tenant, TO, FROM, null, null, "UTC", 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reports.getSalesReport(tenant, FROM, FROM.plusDays(367), null, null, "UTC", 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void apiAcceptsOptionalFiltersAndRequiresTenantAuthentication() throws Exception {
        mvc.perform(get("/api/admin/reports/sales").param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports/sales").cookie(login()).param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(212.50));
        mvc.perform(get("/api/admin/reports/sales").cookie(login()).param("from", FROM.toString()).param("to", TO.toString())
                        .param("menuItemId", tea.getId().toString()).param("tableId", table1.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(32.50))
                .andExpect(jsonPath("$.statements[0].billTotalAmount").value(82.50));
        mvc.perform(get("/api/admin/reports/sales").cookie(login()).param("from", FROM.toString()).param("to", TO.toString())
                        .param("menuItemId", foreignItem.getId().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/reports/sales").cookie(login()).param("from", FROM.toString()).param("to", TO.toString())
                        .param("menuItemId", "invalid")).andExpect(status().isBadRequest());
    }

    private SalesReportResponse report(UUID tableId, UUID itemId, int page, String zone) {
        return reports.getSalesReport(tenant, FROM, TO, tableId, itemId, zone, page, 1);
    }

    private UUID restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Report test");
        restaurant.setLocation("Test");
        return restaurants.saveAndFlush(restaurant).getId();
    }

    private RestaurantTable table(UUID tenantId, String name) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurantId(tenantId);
        table.setTableNumber(name);
        table.setQrCodeUrl("https://example.com/menu");
        return tables.saveAndFlush(table);
    }

    private MenuItem menuItem(UUID tenantId, String name) {
        MenuCategory category = new MenuCategory();
        category.setRestaurantId(tenantId);
        category.setName(name + " category");
        category.setDisplayOrder(1);
        category = categories.saveAndFlush(category);
        MenuItem item = new MenuItem();
        item.setRestaurantId(tenantId);
        item.setCategoryId(category.getId());
        item.setName(name);
        item.setDescription("Test");
        item.setPrice(new BigDecimal("999.00"));
        return menuItems.saveAndFlush(item);
    }

    private DiningSession session(RestaurantTable table, String closedAt) {
        DiningSession session = new DiningSession();
        session.setId(UUID.randomUUID());
        session.setRestaurantId(table.getRestaurantId());
        session.setTableId(table.getId());
        session.setOpenedAt(FROM.minusDays(1));
        session.setStatus(closedAt == null ? DiningSessionStatus.OPEN : DiningSessionStatus.SETTLED);
        session.setClosedAt(closedAt == null ? null : OffsetDateTime.parse(closedAt));
        return sessions.saveAndFlush(session);
    }

    private void order(DiningSession session, Sale... sales) {
        CustomerOrder order = new CustomerOrder();
        order.setRestaurantId(session.getRestaurantId());
        order.setTableId(session.getTableId());
        order.setSessionId(session.getId());
        order.setCustomerName("Guest");
        order.setStatus(OrderStatus.SERVED);
        order = orders.saveAndFlush(order);
        for (Sale sale : sales) {
            OrderItem item = new OrderItem();
            item.setRestaurantId(session.getRestaurantId());
            item.setOrderId(order.getId());
            item.setMenuItemId(sale.item().getId());
            item.setMenuItemNameAtOrderTime("Original " + sale.item().getName());
            item.setQuantity(sale.quantity());
            item.setPriceAtOrderTime(new BigDecimal(sale.price()));
            items.saveAndFlush(item);
            session.setTotalItems(session.getTotalItems() + sale.quantity());
            session.setTotalAmount(session.getTotalAmount().add(item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(sale.quantity()))));
        }
        session.setTotalOrders(session.getTotalOrders() + 1);
        sessions.saveAndFlush(session);
    }

    private Cookie login() {
        RestaurantAdmin admin = new RestaurantAdmin();
        admin.setRestaurantId(tenant);
        admin.setUsername("report-" + UUID.randomUUID());
        admin.setUsernameNormalized(admin.getUsername());
        admin.setEmail(admin.getUsername() + "@example.com");
        admin.setEmailNormalized(admin.getEmail());
        admin.setPasswordHash("unused-by-token-tests");
        accounts.saveAndFlush(admin);
        return new Cookie("restaurant_access_token", jwtService.createAccessToken(admin));
    }

    private record Sale(MenuItem item, int quantity, String price) {}
}
