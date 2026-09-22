package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.IdempotencyKey;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.IdempotencyKeyRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {
    @Test
    void aKeyNeverRevealsAnOrderFromAnotherTableIncludingRaceRecovery() {
        var keys = mock(IdempotencyKeyRepository.class);
        var orders = mock(CustomerOrderRepository.class);
        var mapper = mock(OrderMapper.class);
        var items = mock(OrderItemRepository.class);
        var service = new IdempotencyService(keys, orders, mapper, items);
        var order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setTableId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        var key = new IdempotencyKey();
        key.setOrderId(order.getId());
        when(keys.findByKey("reused")).thenReturn(Optional.of(key));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        UUID otherTable = UUID.randomUUID();
        assertThatThrownBy(() -> service.findExistingResponse("reused", otherTable))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.findExistingResponseOrThrow("reused", otherTable,
                new DataIntegrityViolationException("duplicate"))).isInstanceOf(ConflictException.class);
        verifyNoInteractions(mapper, items);
    }
}
