package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.OrderStatus;
import com.nihal.restaurantordering.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class OrderStatusTransitionValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, Set.of(OrderStatus.SERVED),
            OrderStatus.SERVED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    public void validate(OrderStatus currentStatus, OrderStatus nextStatus) {
        if (ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(nextStatus)) {
            return;
        }
        throw new ConflictException(buildMessage(currentStatus, nextStatus));
    }

    private String buildMessage(OrderStatus currentStatus, OrderStatus nextStatus) {
        return switch (currentStatus) {
            case SERVED -> "Order already served, cannot update status to " + nextStatus;
            case CANCELLED -> "Cancelled order cannot be updated to " + nextStatus;
            case PLACED -> "Order in PLACED state can only move to PREPARING";
            case PREPARING -> "Order in PREPARING state can only move to SERVED";
        };
    }
}
