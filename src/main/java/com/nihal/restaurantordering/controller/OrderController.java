package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.dto.order.OrdersResponse;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import com.nihal.restaurantordering.dto.order.UpdateOrderStatusRequest;
import com.nihal.restaurantordering.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request
    ) {
        return orderService.placeOrder(idempotencyKey, request);
    }

    @GetMapping
    public OrdersResponse getOrdersByTable(
            @RequestParam @NotNull UUID tableId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderService.getOrdersByTable(tableId, page, size);
    }

    @PutMapping("/{orderId}/status")
    public OrderResponse updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestHeader("X-Restaurant-Id") UUID restaurantId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(orderId, restaurantId, request.status());
    }
}
