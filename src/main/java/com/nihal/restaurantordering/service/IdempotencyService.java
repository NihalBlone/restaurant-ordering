package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.IdempotencyKey;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.IdempotencyKeyRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderMapper orderMapper;
    private final OrderItemRepository orderItemRepository;

    public String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 120) {
            throw new BadRequestException("X-Idempotency-Key must not exceed 120 characters");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public OrderResponse findExistingResponse(String key, UUID tableId) {
        return idempotencyKeyRepository.findByKey(key)
                .map(IdempotencyKey::getOrderId)
                .map(orderId -> loadOrderResponse(orderId, tableId))
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void claimKey(String key, UUID orderId) {
        if (key == null) {
            return;
        }

        IdempotencyKey idempotencyKey = new IdempotencyKey();
        idempotencyKey.setKey(key);
        idempotencyKey.setOrderId(orderId);
        idempotencyKeyRepository.saveAndFlush(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public OrderResponse findExistingResponseOrThrow(String key, UUID tableId, DataIntegrityViolationException exception) {
        OrderResponse response = findExistingResponse(key, tableId);
        if (response != null) {
            return response;
        }
        throw exception;
    }

    private OrderResponse loadOrderResponse(UUID orderId, UUID tableId) {
        CustomerOrder order = customerOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Idempotent order not found for id " + orderId));
        if (!tableId.equals(order.getTableId())) {
            throw new ConflictException("This idempotency key is already used for a different table");
        }
        return orderMapper.toOrderResponse(order, orderItemRepository.findAllByOrderIdIn(List.of(orderId)));
    }
}
