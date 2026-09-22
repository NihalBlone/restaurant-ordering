package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.DiningSession;
import com.nihal.restaurantordering.domain.DiningSessionStatus;
import com.nihal.restaurantordering.domain.MenuCategory;
import com.nihal.restaurantordering.domain.MenuItem;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.OrderStatus;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.order.OrderRequestItem;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.dto.order.OrdersResponse;
import com.nihal.restaurantordering.dto.order.PlaceOrderRequest;
import com.nihal.restaurantordering.events.OrderCreatedEvent;
import com.nihal.restaurantordering.events.OrderStatusChangedEvent;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.ForbiddenException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.DiningSessionRepository;
import com.nihal.restaurantordering.repository.MenuCategoryRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import com.nihal.restaurantordering.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final long TABLE_SESSION_HOURS = 3L;

    private final RestaurantContextService restaurantContextService;
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final DiningSessionRepository diningSessionRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final InputSanitizer inputSanitizer;
    private final IdempotencyService idempotencyService;
    private final TableOrderRateLimiter tableOrderRateLimiter;
    private final OrderStatusTransitionValidator orderStatusTransitionValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse placeOrder(String idempotencyKey,
                                    String orderingClientId,
                                    PlaceOrderRequest request) {
        RestaurantTable table = restaurantContextService.getActiveTableForUpdate(request.tableId());
        String normalizedIdempotencyKey = idempotencyService.normalizeKey(idempotencyKey);

        if (normalizedIdempotencyKey != null) {
            OrderResponse existingOrder = idempotencyService.findExistingResponse(normalizedIdempotencyKey);
            if (existingOrder != null) {
                log.info("Reusing order {} for idempotency key {}", existingOrder.orderId(), normalizedIdempotencyKey);
                return existingOrder;
            }
        }

        return createOrder(table, normalizedIdempotencyKey, orderingClientId, request);
    }

    @Transactional(readOnly = true)
    public OrdersResponse getOrdersByTable(UUID tableId, UUID sessionId, int page, int size) {
        RestaurantTable table = restaurantContextService.getActiveTable(tableId);
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerOrder> ordersPage = fetchOrdersPage(table, sessionId, pageable);
        Map<UUID, List<OrderItem>> itemsByOrderId = groupOrderItems(ordersPage.getContent().stream().map(CustomerOrder::getId).toList());

        List<OrderResponse> orders = ordersPage.getContent().stream()
                .map(order -> orderMapper.toOrderResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();

        UUID resolvedSessionId = table.getCurrentSessionId() != null
                ? table.getCurrentSessionId()
                : orders.stream().findFirst().map(OrderResponse::sessionId).orElse(null);
        if (resolvedSessionId == null && table.getLastSessionClosedAt() == null) {
            resolvedSessionId = sessionId;
        }
        SessionSummary sessionSummary = summarizeSession(tableId, resolvedSessionId);

        return OrdersResponse.builder()
                .tableId(tableId)
                .tableNumber(table.getTableNumber())
                .sessionId(resolvedSessionId)
                .sessionActive(table.getCurrentSessionId() != null
                        && table.getCurrentSessionId().equals(resolvedSessionId))
                .sessionTotalItems(sessionSummary.totalItems())
                .sessionTotalAmount(sessionSummary.totalAmount())
                .page(ordersPage.getNumber())
                .size(ordersPage.getSize())
                .totalElements(ordersPage.getTotalElements())
                .totalPages(ordersPage.getTotalPages())
                .orders(orders)
                .build();
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UUID restaurantId, OrderStatus nextStatus) {
        CustomerOrder order = customerOrderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Order not found for id " + orderId + " in restaurant " + restaurantId));

        OrderStatus previousStatus = order.getStatus();
        orderStatusTransitionValidator.validate(order.getStatus(), nextStatus);
        order.setStatus(nextStatus);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        OrderResponse response = buildOrderResponse(savedOrder);
        log.info("Order status updated. restaurantId={} tableId={} orderId={} previousStatus={} newStatus={}",
                restaurantId, savedOrder.getTableId(), orderId, previousStatus, nextStatus);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(response));
        return response;
    }

    private OrderResponse createOrder(RestaurantTable table,
                                      String idempotencyKey,
                                      String orderingClientId,
                                      PlaceOrderRequest request) {
        String customerName = inputSanitizer.sanitizeCustomerName(request.customerName());
        Map<UUID, Integer> quantitiesByMenuItem = aggregateOrderItems(request.items());
        List<MenuItem> menuItems = menuItemRepository.findAllByIdInAndRestaurantIdAndAvailableTrue(quantitiesByMenuItem.keySet(), table.getRestaurantId());
        validateMenuItems(table, quantitiesByMenuItem.keySet(), menuItems);
        Instant rateLimitToken = tableOrderRateLimiter.acquire(table.getId(), orderingClientId);

        try {
            UUID sessionId = resolveSessionId(table, request.sessionId());
            CustomerOrder order = new CustomerOrder();
            order.setRestaurantId(table.getRestaurantId());
            order.setTableId(table.getId());
            order.setSessionId(sessionId);
            order.setCustomerName(customerName);
            order.setStatus(OrderStatus.PLACED);

            CustomerOrder savedOrder = customerOrderRepository.save(order);
            idempotencyService.claimKey(idempotencyKey, savedOrder.getId());
            Map<UUID, MenuItem> menuItemById = menuItems.stream().collect(Collectors.toMap(MenuItem::getId, Function.identity()));
            List<OrderItem> orderItems = quantitiesByMenuItem.entrySet().stream()
                    .map(entry -> toOrderItem(savedOrder, menuItemById.get(entry.getKey()), entry.getValue()))
                    .toList();

            List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);
            OrderResponse response = orderMapper.toOrderResponse(savedOrder, savedItems);
            log.info("Order created. restaurantId={} tableId={} orderId={} sessionId={} totalItems={} totalAmount={}",
                    savedOrder.getRestaurantId(),
                    savedOrder.getTableId(),
                    savedOrder.getId(),
                    savedOrder.getSessionId(),
                    response.totalItems(),
                    response.estimatedTotalAmount());
            eventPublisher.publishEvent(new OrderCreatedEvent(response));
            return response;
        } catch (RuntimeException exception) {
            tableOrderRateLimiter.release(table.getId(), orderingClientId, rateLimitToken);
            throw exception;
        }
    }

    private OrderItem toOrderItem(CustomerOrder order, MenuItem menuItem, Integer quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.setRestaurantId(order.getRestaurantId());
        orderItem.setOrderId(order.getId());
        orderItem.setMenuItemId(menuItem.getId());
        orderItem.setMenuItemNameAtOrderTime(menuItem.getName());
        orderItem.setQuantity(quantity);
        orderItem.setPriceAtOrderTime(menuItem.getPrice());
        return orderItem;
    }

    private Map<UUID, Integer> aggregateOrderItems(List<OrderRequestItem> items) {
        Map<UUID, Integer> aggregated = new LinkedHashMap<>();
        for (OrderRequestItem item : items) {
            aggregated.merge(item.menuItemId(), item.quantity(), Integer::sum);
        }
        return aggregated;
    }

    private void validateMenuItems(RestaurantTable table, Collection<UUID> requestedItemIds, List<MenuItem> menuItems) {
        if (menuItems.size() != requestedItemIds.size()) {
            throw new BadRequestException("One or more menu items are invalid, unavailable, or belong to another restaurant");
        }

        boolean crossRestaurant = menuItems.stream().anyMatch(item -> !table.getRestaurantId().equals(item.getRestaurantId()));
        if (crossRestaurant) {
            throw new ForbiddenException("Cross-restaurant access is not allowed");
        }

        Set<UUID> categoryIds = menuItems.stream()
                .map(MenuItem::getCategoryId)
                .collect(Collectors.toCollection(HashSet::new));
        List<MenuCategory> categories = menuCategoryRepository.findAllByIdInAndRestaurantId(categoryIds, table.getRestaurantId());
        if (categories.size() != categoryIds.size()) {
            throw new ForbiddenException("Menu items must belong to categories in the same restaurant as the table");
        }
    }

    private OrderResponse buildOrderResponse(CustomerOrder order) {
        List<OrderItem> items = orderItemRepository.findAllByOrderIdIn(List.of(order.getId()));
        return orderMapper.toOrderResponse(order, items);
    }

    private Map<UUID, List<OrderItem>> groupOrderItems(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemRepository.findAllByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private UUID resolveSessionId(RestaurantTable table, UUID requestedSessionId) {
        if (table.getCurrentSessionId() == null) {
            UUID newSessionId = UUID.randomUUID();
            table.setCurrentSessionId(newSessionId);
            DiningSession diningSession = new DiningSession();
            diningSession.setId(newSessionId);
            diningSession.setRestaurantId(table.getRestaurantId());
            diningSession.setTableId(table.getId());
            diningSession.setStatus(DiningSessionStatus.OPEN);
            diningSession.setOpenedAt(OffsetDateTime.now(ZoneOffset.UTC));
            diningSession.setTotalOrders(0);
            diningSession.setTotalItems(0);
            diningSession.setTotalAmount(BigDecimal.ZERO);
            diningSessionRepository.save(diningSession);
            log.info("Started table session {} for table {}", newSessionId, table.getId());
            return newSessionId;
        }

        if (requestedSessionId != null && !requestedSessionId.equals(table.getCurrentSessionId())) {
            log.info("Ignoring stale client session {} for table {}; active session is {}",
                    requestedSessionId, table.getId(), table.getCurrentSessionId());
        }
        return table.getCurrentSessionId();
    }

    private Page<CustomerOrder> fetchOrdersPage(RestaurantTable table, UUID requestedSessionId, Pageable pageable) {
        if (table.getCurrentSessionId() != null) {
            return customerOrderRepository.findByTableIdAndSessionIdOrderByCreatedAtDesc(
                    table.getId(), table.getCurrentSessionId(), pageable);
        }
        if (table.getLastSessionClosedAt() != null) {
            return Page.empty(pageable);
        }
        if (requestedSessionId != null) {
            return customerOrderRepository.findByTableIdAndSessionIdOrderByCreatedAtDesc(
                    table.getId(), requestedSessionId, pageable);
        }
        OffsetDateTime sessionStart = OffsetDateTime.now(ZoneOffset.UTC).minusHours(TABLE_SESSION_HOURS);
        return customerOrderRepository.findRecentOrdersByTableId(table.getId(), sessionStart, pageable);
    }

    private SessionSummary summarizeSession(UUID tableId, UUID sessionId) {
        if (sessionId == null) {
            return new SessionSummary(0, BigDecimal.ZERO);
        }
        List<UUID> orderIds = customerOrderRepository
                .findAllByTableIdAndSessionIdOrderByCreatedAtDesc(tableId, sessionId)
                .stream()
                .map(CustomerOrder::getId)
                .toList();
        List<OrderItem> items = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findAllByOrderIdIn(orderIds);
        int totalItems = items.stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SessionSummary(totalItems, totalAmount);
    }

    private record SessionSummary(int totalItems, BigDecimal totalAmount) {
    }
}
