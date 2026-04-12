package com.nihal.restaurantordering.events;

import com.nihal.restaurantordering.dto.order.OrderResponse;

public record OrderStatusChangedEvent(OrderResponse order) {
}
