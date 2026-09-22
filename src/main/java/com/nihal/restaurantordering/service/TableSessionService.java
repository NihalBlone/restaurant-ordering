package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.DiningSession;
import com.nihal.restaurantordering.domain.DiningSessionStatus;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.OrderStatus;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.session.TableSessionCloseResponse;
import com.nihal.restaurantordering.events.TableSessionClosedEvent;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.exception.ForbiddenException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.DiningSessionRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableSessionService {

    private final RestaurantContextService restaurantContextService;
    private final CustomerOrderRepository customerOrderRepository;
    private final DiningSessionRepository diningSessionRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TableSessionCloseResponse closeCurrentSession(UUID tableId, UUID restaurantId) {
        RestaurantTable table = restaurantContextService.getActiveTableForUpdate(tableId);
        if (!restaurantId.equals(table.getRestaurantId())) {
            throw new ForbiddenException("Table does not belong to this restaurant");
        }
        if (table.getCurrentSessionId() == null) {
            throw new ConflictException("This table has no active session to finalize");
        }

        UUID sessionId = table.getCurrentSessionId();
        List<CustomerOrder> orders = customerOrderRepository
                .findAllByTableIdAndSessionIdOrderByCreatedAtDesc(tableId, sessionId);
        boolean hasOpenOrders = orders.stream()
                .anyMatch(order -> order.getStatus() == OrderStatus.PLACED
                        || order.getStatus() == OrderStatus.PREPARING);
        if (hasOpenOrders) {
            throw new ConflictException("Serve or cancel every order before finalizing the table bill");
        }

        List<UUID> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<OrderItem> items = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findAllByOrderIdIn(orderIds);
        int totalItems = items.stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OffsetDateTime closedAt = OffsetDateTime.now(ZoneOffset.UTC);

        DiningSession diningSession = diningSessionRepository
                .findByIdAndRestaurantId(sessionId, restaurantId)
                .orElseGet(() -> legacySession(sessionId, table, orders));
        diningSession.setStatus(DiningSessionStatus.SETTLED);
        diningSession.setClosedAt(closedAt);
        diningSession.setTotalOrders(orders.size());
        diningSession.setTotalItems(totalItems);
        diningSession.setTotalAmount(totalAmount);
        diningSessionRepository.save(diningSession);

        table.setCurrentSessionId(null);
        table.setLastSessionClosedAt(closedAt);

        TableSessionCloseResponse response = TableSessionCloseResponse.builder()
                .tableId(tableId)
                .tableNumber(table.getTableNumber())
                .restaurantId(restaurantId)
                .closedSessionId(sessionId)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .closedAt(closedAt)
                .build();
        log.info("Table session finalized. restaurantId={} tableId={} sessionId={} totalAmount={}",
                restaurantId, tableId, sessionId, totalAmount);
        eventPublisher.publishEvent(new TableSessionClosedEvent(response));
        return response;
    }

    private DiningSession legacySession(UUID sessionId,
                                        RestaurantTable table,
                                        List<CustomerOrder> orders) {
        DiningSession session = new DiningSession();
        session.setId(sessionId);
        session.setRestaurantId(table.getRestaurantId());
        session.setTableId(table.getId());
        session.setOpenedAt(orders.stream()
                .map(CustomerOrder::getCreatedAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElseGet(() -> OffsetDateTime.now(ZoneOffset.UTC)));
        return session;
    }
}
