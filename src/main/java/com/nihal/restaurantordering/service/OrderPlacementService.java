package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderResponse placeOrder(String idempotencyKey,
                                    String orderingClientId,
                                    PlaceOrderRequest request) {
        String normalizedKey = idempotencyService.normalizeKey(idempotencyKey);
        try {
            return orderService.placeOrder(normalizedKey, orderingClientId, request);
        } catch (DataIntegrityViolationException exception) {
            if (normalizedKey == null) {
                throw exception;
            }
            OrderResponse existing = idempotencyService
                    .findExistingResponseOrThrow(normalizedKey, request.tableId(), exception);
            log.info("Concurrent request reused order {} for idempotency key {}",
                    existing.orderId(), normalizedKey);
            return existing;
        }
    }
}
