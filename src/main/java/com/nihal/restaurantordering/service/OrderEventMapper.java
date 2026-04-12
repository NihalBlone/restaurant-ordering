package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.dto.events.OrderEventDTO;
import com.nihal.restaurantordering.dto.events.OrderEventItemDTO;
import com.nihal.restaurantordering.dto.order.OrderItemResponse;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OrderEventMapper {

    public OrderEventDTO toEvent(OrderResponse orderResponse, OffsetDateTime timestamp) {
        List<OrderEventItemDTO> items = orderResponse.items().stream()
                .map(this::toEventItem)
                .toList();

        return OrderEventDTO.builder()
                .orderId(orderResponse.orderId())
                .tableId(orderResponse.tableId())
                .restaurantId(orderResponse.restaurantId())
                .sessionId(orderResponse.sessionId())
                .status(orderResponse.status())
                .items(items)
                .totalAmount(orderResponse.estimatedTotalAmount())
                .timestamp(timestamp)
                .build();
    }

    private OrderEventItemDTO toEventItem(OrderItemResponse item) {
        return OrderEventItemDTO.builder()
                .menuItemId(item.menuItemId())
                .menuItemName(item.menuItemName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .lineTotal(item.lineTotal())
                .build();
    }
}
