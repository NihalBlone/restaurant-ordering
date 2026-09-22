package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.MenuItem;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.order.OrderRequestItem;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderIdempotencyIntegrationTest {

    @Autowired
    private OrderPlacementService orderPlacementService;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Test
    void retryWithSameKeyReturnsThePersistedOrder() {
        RestaurantTable table = restaurantTableRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
        MenuItem item = menuItemRepository.findAll().stream()
                .filter(candidate -> candidate.getRestaurantId().equals(table.getRestaurantId()))
                .findFirst()
                .orElseThrow();
        long ordersBefore = customerOrderRepository.count();
        String idempotencyKey = "integration-" + UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest(
                table.getId(),
                null,
                null,
                List.of(new OrderRequestItem(item.getId(), 1))
        );

        var firstResponse = orderPlacementService.placeOrder(
                idempotencyKey,
                "integration-client-" + UUID.randomUUID(),
                request
        );
        var retriedResponse = orderPlacementService.placeOrder(
                idempotencyKey,
                "another-client-" + UUID.randomUUID(),
                request
        );

        assertThat(retriedResponse.orderId()).isEqualTo(firstResponse.orderId());
        assertThat(retriedResponse.sessionId()).isEqualTo(firstResponse.sessionId());
        assertThat(retriedResponse.customerName()).isEqualTo("Guest");
        assertThat(customerOrderRepository.count()).isEqualTo(ordersBefore + 1);
    }
}
