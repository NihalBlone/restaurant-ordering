package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.dto.order.OrderItemResponse;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toOrderResponse(CustomerOrder order, List<OrderItem> orderItems) {
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(this::toOrderItemResponse)
                .toList();

        int totalItems = orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        BigDecimal estimatedTotal = orderItems.stream()
                .map(item -> item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .orderId(order.getId())
                .restaurantId(order.getRestaurantId())
                .tableId(order.getTableId())
                .customerName(order.getCustomerName())
                .status(order.getStatus())
                .totalItems(totalItems)
                .estimatedTotalAmount(estimatedTotal)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        BigDecimal lineTotal = item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemResponse.builder()
                .orderItemId(item.getId())
                .menuItemId(item.getMenuItemId())
                .menuItemName(item.getMenuItemNameAtOrderTime())
                .quantity(item.getQuantity())
                .unitPrice(item.getPriceAtOrderTime())
                .lineTotal(lineTotal)
                .build();
    }
}
