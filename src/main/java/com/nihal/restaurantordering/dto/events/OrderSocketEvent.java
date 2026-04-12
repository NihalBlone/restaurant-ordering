package com.nihal.restaurantordering.dto.events;

import com.nihal.restaurantordering.dto.order.OrderResponse;
import lombok.Builder;

@Builder
public record OrderSocketEvent(
        String eventType,
        OrderResponse order
) {
}
