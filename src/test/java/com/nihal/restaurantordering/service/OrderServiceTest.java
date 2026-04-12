package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.OrderStatus;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.dto.order.OrdersResponse;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import com.nihal.restaurantordering.util.InputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private RestaurantContextService restaurantContextService;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private CustomerOrderRepository customerOrderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InputSanitizer inputSanitizer;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private TableOrderRateLimiter tableOrderRateLimiter;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderMapper orderMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderMapper = new OrderMapper();
        orderService = new OrderService(
                restaurantContextService,
                menuItemRepository,
                customerOrderRepository,
                orderItemRepository,
                orderMapper,
                inputSanitizer,
                idempotencyService,
                tableOrderRateLimiter,
                eventPublisher
        );
    }

    @Test
    void placeOrderReturnsExistingOrderWhenIdempotencyKeyAlreadyExists() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = new RestaurantTable();
        table.setId(tableId);
        table.setRestaurantId(UUID.randomUUID());

        OrderResponse existingOrder = OrderResponse.builder()
                .orderId(UUID.randomUUID())
                .restaurantId(table.getRestaurantId())
                .tableId(tableId)
                .customerName("Nihal")
                .status(OrderStatus.PLACED)
                .totalItems(1)
                .estimatedTotalAmount(new BigDecimal("100.00"))
                .items(List.of())
                .build();

        when(restaurantContextService.getActiveTable(tableId)).thenReturn(table);
        when(idempotencyService.normalizeKey(" idem-1 ")).thenReturn("idem-1");
        when(idempotencyService.findExistingResponse("idem-1")).thenReturn(existingOrder);

        OrderResponse response = orderService.placeOrder(" idem-1 ", new PlaceOrderRequest(tableId, "Nihal", List.of()));

        assertThat(response).isEqualTo(existingOrder);
        verify(customerOrderRepository, never()).save(any());
        verify(tableOrderRateLimiter, never()).acquire(any());
    }

    @Test
    void getOrdersByTableUsesThreeHourSessionWindow() {
        UUID tableId = UUID.randomUUID();
        RestaurantTable table = new RestaurantTable();
        table.setId(tableId);
        when(restaurantContextService.getActiveTable(tableId)).thenReturn(table);
        when(customerOrderRepository.findRecentOrdersByTableId(eq(tableId), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        OrdersResponse response = orderService.getOrdersByTable(tableId, 0, 20);

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(customerOrderRepository).findRecentOrdersByTableId(eq(tableId), cutoffCaptor.capture(), any(Pageable.class));
        OffsetDateTime cutoff = cutoffCaptor.getValue();
        java.time.Instant expectedLowerBound = OffsetDateTime.now().minusHours(3).minusSeconds(5).toInstant();
        java.time.Instant expectedUpperBound = OffsetDateTime.now().minusHours(3).plusSeconds(5).toInstant();

        assertThat(cutoff.toInstant()).isBetween(expectedLowerBound, expectedUpperBound);
        assertThat(response.orders()).isEmpty();
    }

    @Test
    void updateOrderStatusRejectsInvalidTransitions() {
        UUID orderId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        CustomerOrder order = new CustomerOrder();
        order.setId(orderId);
        order.setRestaurantId(restaurantId);
        order.setTableId(UUID.randomUUID());
        order.setStatus(OrderStatus.PLACED);

        when(customerOrderRepository.findByIdAndRestaurantId(orderId, restaurantId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, restaurantId, OrderStatus.SERVED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid order status transition");

        verify(customerOrderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatusAllowsPlacedToPreparing() {
        UUID orderId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        CustomerOrder order = new CustomerOrder();
        order.setId(orderId);
        order.setRestaurantId(restaurantId);
        order.setTableId(tableId);
        order.setCustomerName("Nihal");
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        order.setUpdatedAt(OffsetDateTime.now().minusMinutes(1));

        when(customerOrderRepository.findByIdAndRestaurantId(orderId, restaurantId)).thenReturn(Optional.of(order));
        when(customerOrderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderIdIn(List.of(orderId))).thenReturn(List.of());

        OrderResponse response = orderService.updateOrderStatus(orderId, restaurantId, OrderStatus.PREPARING);

        assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);
    }
}
