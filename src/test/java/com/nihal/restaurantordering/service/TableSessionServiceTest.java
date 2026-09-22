package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.DiningSession;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.OrderStatus;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.events.TableSessionClosedEvent;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.DiningSessionRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableSessionServiceTest {

    @Mock
    private RestaurantContextService restaurantContextService;
    @Mock
    private CustomerOrderRepository customerOrderRepository;
    @Mock
    private DiningSessionRepository diningSessionRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TableSessionService tableSessionService;

    @BeforeEach
    void setUp() {
        tableSessionService = new TableSessionService(
                restaurantContextService,
                customerOrderRepository,
                diningSessionRepository,
                orderItemRepository,
                eventPublisher
        );
    }

    @Test
    void closeCurrentSessionRejectsTablesWithOrdersStillInService() {
        UUID restaurantId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RestaurantTable table = table(restaurantId, tableId, sessionId);
        CustomerOrder preparing = order(tableId, sessionId, OrderStatus.PREPARING);

        when(restaurantContextService.getActiveTableForUpdate(tableId)).thenReturn(table);
        when(customerOrderRepository.findAllByTableIdAndSessionIdOrderByCreatedAtDesc(tableId, sessionId))
                .thenReturn(List.of(preparing));

        assertThatThrownBy(() -> tableSessionService.closeCurrentSession(tableId, restaurantId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Serve or cancel");

        assertThat(table.getCurrentSessionId()).isEqualTo(sessionId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void closeCurrentSessionCalculatesBillAndClearsActiveSession() {
        UUID restaurantId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RestaurantTable table = table(restaurantId, tableId, sessionId);
        CustomerOrder served = order(tableId, sessionId, OrderStatus.SERVED);
        OrderItem item = new OrderItem();
        item.setOrderId(served.getId());
        item.setQuantity(2);
        item.setPriceAtOrderTime(new BigDecimal("125.50"));

        when(restaurantContextService.getActiveTableForUpdate(tableId)).thenReturn(table);
        when(customerOrderRepository.findAllByTableIdAndSessionIdOrderByCreatedAtDesc(tableId, sessionId))
                .thenReturn(List.of(served));
        when(orderItemRepository.findAllByOrderIdIn(List.of(served.getId())))
                .thenReturn(List.of(item));
        when(diningSessionRepository.findByIdAndRestaurantId(sessionId, restaurantId))
                .thenReturn(java.util.Optional.empty());

        var response = tableSessionService.closeCurrentSession(tableId, restaurantId);

        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("251.00");
        assertThat(table.getCurrentSessionId()).isNull();
        assertThat(table.getLastSessionClosedAt()).isNotNull();
        verify(diningSessionRepository).save(any(DiningSession.class));
        verify(eventPublisher).publishEvent(any(TableSessionClosedEvent.class));
    }

    private RestaurantTable table(UUID restaurantId, UUID tableId, UUID sessionId) {
        RestaurantTable table = new RestaurantTable();
        table.setId(tableId);
        table.setRestaurantId(restaurantId);
        table.setTableNumber("T1");
        table.setCurrentSessionId(sessionId);
        return table;
    }

    private CustomerOrder order(UUID tableId, UUID sessionId, OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTableId(tableId);
        order.setSessionId(sessionId);
        order.setStatus(status);
        return order;
    }
}
