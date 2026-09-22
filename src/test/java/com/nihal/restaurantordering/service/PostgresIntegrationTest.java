package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.dto.order.OrderRequestItem;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// CI supplies a disposable PostgreSQL database. Never point this at production.
@SpringBootTest(properties = "app.sample.enabled=false")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired private RestaurantRepository restaurants;
    @Autowired private RestaurantTableRepository tables;
    @Autowired private MenuCategoryRepository categories;
    @Autowired private MenuItemRepository items;
    @Autowired private OrderPlacementService placement;
    @Autowired private OrderService orders;
    @Autowired private TableSessionService sessions;
    @Autowired private SalesReportService reports;
    @Autowired private MenuService menus;

    @Test
    void migrationsConcurrentIdempotencySharedSessionAndSettlementWorkOnPostgres() throws Exception {
        var restaurant = new Restaurant();
        restaurant.setName("PostgreSQL test " + UUID.randomUUID());
        restaurant.setLocation("Test only");
        restaurant = restaurants.saveAndFlush(restaurant);
        var table = new RestaurantTable();
        table.setRestaurantId(restaurant.getId());
        table.setTableNumber("T1");
        table.setQrCodeUrl("https://example.invalid/menu");
        table = tables.saveAndFlush(table);
        var category = new MenuCategory();
        category.setRestaurantId(restaurant.getId());
        category.setName("Coffee");
        category.setDisplayOrder(0);
        category = categories.saveAndFlush(category);
        var item = new MenuItem();
        item.setRestaurantId(restaurant.getId());
        item.setCategoryId(category.getId());
        item.setName("Flat white");
        item.setDescription("Test coffee");
        item.setPrice(new BigDecimal("12.30"));
        item = items.saveAndFlush(item);
        assertThat(menus.getMenuByTable(table.getId()).categories()).hasSize(1);

        var request = new PlaceOrderRequest(table.getId(), null, null,
                List.of(new OrderRequestItem(item.getId(), 2)));
        String key = "postgres-" + UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        OrderResponse first;
        try {
            java.util.concurrent.Callable<OrderResponse> submit = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return placement.placeOrder(key, UUID.randomUUID().toString(), request);
            };
            var one = pool.submit(submit);
            var two = pool.submit(submit);
            first = one.get(30, TimeUnit.SECONDS);
            assertThat(two.get(30, TimeUnit.SECONDS).orderId()).isEqualTo(first.orderId());
        } finally {
            pool.shutdownNow();
        }
        var second = placement.placeOrder("postgres-" + UUID.randomUUID(), UUID.randomUUID().toString(), request);
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(orders.getOrdersByTable(table.getId(), null, 0, 10).sessionTotalAmount())
                .isEqualByComparingTo("49.20");
        for (var order : List.of(first, second)) {
            orders.updateOrderStatus(order.orderId(), restaurant.getId(), OrderStatus.PREPARING);
            orders.updateOrderStatus(order.orderId(), restaurant.getId(), OrderStatus.SERVED);
        }
        assertThat(sessions.closeCurrentSession(table.getId(), restaurant.getId()).totalAmount())
                .isEqualByComparingTo("49.20");
        assertThat(orders.getOrdersByTable(table.getId(), first.sessionId(), 0, 10).orders()).isEmpty();
        var now = OffsetDateTime.now();
        var report = reports.getSalesReport(restaurant.getId(), now.minusDays(1), now.plusDays(1),
                table.getId(), item.getId(), "Asia/Kolkata", 0, 10);
        assertThat(report.totalAmount()).isEqualByComparingTo("49.20");
        assertThat(report.settledBills()).isEqualTo(1);
        assertThat(report.totalItems()).isEqualTo(4);
        var next = placement.placeOrder("postgres-" + UUID.randomUUID(), UUID.randomUUID().toString(), request);
        assertThat(next.sessionId()).isNotEqualTo(first.sessionId());
    }
}
