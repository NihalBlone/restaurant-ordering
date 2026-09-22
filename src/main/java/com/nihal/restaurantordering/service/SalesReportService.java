package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.CustomerOrder;
import com.nihal.restaurantordering.domain.DiningSession;
import com.nihal.restaurantordering.domain.DiningSessionStatus;
import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.order.OrderResponse;
import com.nihal.restaurantordering.dto.report.SalesReportResponse;
import com.nihal.restaurantordering.dto.report.SalesStatementEntry;
import com.nihal.restaurantordering.dto.report.TableSalesSummary;
import com.nihal.restaurantordering.dto.report.ItemSalesSummary;
import com.nihal.restaurantordering.dto.report.DailySalesSummary;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.CustomerOrderRepository;
import com.nihal.restaurantordering.repository.DiningSessionRepository;
import com.nihal.restaurantordering.repository.OrderItemRepository;
import com.nihal.restaurantordering.repository.MenuItemRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesReportService {

    private static final Duration MAX_REPORT_RANGE = Duration.ofDays(366);

    private final DiningSessionRepository diningSessionRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesReportResponse getSalesReport(UUID restaurantId,
                                              OffsetDateTime from,
                                              OffsetDateTime to,
                                              UUID tableId,
                                              UUID menuItemId,
                                              String timeZone,
                                              int page,
                                              int size) {
        validateRange(from, to);
        ZoneId zone = reportZone(timeZone);
        if (tableId != null) {
            restaurantTableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                    .orElseThrow(() -> new NotFoundException("Table not found for this restaurant"));
        }
        if (menuItemId != null) {
            menuItemRepository.findByIdAndRestaurantId(menuItemId, restaurantId)
                    .orElseThrow(() -> new NotFoundException("Food item not found for this restaurant"));
        }

        Page<DiningSession> statementPage = diningSessionRepository.findStatementPage(
                restaurantId,
                DiningSessionStatus.SETTLED,
                from,
                to,
                tableId,
                menuItemId,
                PageRequest.of(page, size)
        );
        List<DiningSession> allSessions = diningSessionRepository.findAllForTotals(
                restaurantId,
                DiningSessionStatus.SETTLED,
                from,
                to,
                tableId,
                menuItemId
        );
        List<OrderItemRepository.SalesRow> salesRows = orderItemRepository.findSalesRows(
                restaurantId, DiningSessionStatus.SETTLED, from, to, tableId, menuItemId);
        // Item-filtered amounts must never include the other dishes on the same bill.
        Map<UUID, Totals> totalsBySession = menuItemId == null
                ? allSessions.stream().collect(Collectors.toMap(DiningSession::getId,
                    session -> new Totals(session.getTotalOrders(), session.getTotalItems(), session.getTotalAmount())))
                : salesRows.stream().collect(Collectors.toMap(OrderItemRepository.SalesRow::getSessionId,
                    row -> new Totals(row.getTotalOrders(), row.getTotalItems(), row.getTotalAmount())));
        Totals totals = sumTotals(allSessions, totalsBySession);
        Map<UUID, RestaurantTable> tableById = restaurantTableRepository
                .findAllByRestaurantIdOrderByTableNumberAsc(restaurantId)
                .stream()
                .collect(Collectors.toMap(RestaurantTable::getId, table -> table));

        Map<UUID, List<OrderResponse>> ordersBySession = loadOrdersBySession(
                restaurantId,
                statementPage.getContent().stream().map(DiningSession::getId).toList(),
                menuItemId
        );

        List<SalesStatementEntry> statements = statementPage.getContent().stream()
                .map(session -> SalesStatementEntry.builder()
                        .sessionId(session.getId())
                        .tableId(session.getTableId())
                        .tableNumber(tableNumber(tableById, session.getTableId()))
                        .openedAt(session.getOpenedAt())
                        .settledAt(session.getClosedAt())
                        .totalOrders(totalsBySession.get(session.getId()).orders())
                        .totalItems(totalsBySession.get(session.getId()).items())
                        .totalAmount(totalsBySession.get(session.getId()).amount())
                        .billTotalAmount(session.getTotalAmount())
                        .orders(ordersBySession.getOrDefault(session.getId(), List.of()))
                        .build())
                .toList();

        return SalesReportResponse.builder()
                .from(from)
                .to(to)
                .tableId(tableId)
                .menuItemId(menuItemId)
                .timeZone(zone.getId())
                .settledBills(allSessions.size())
                .totalOrders(totals.orders())
                .totalItems(totals.items())
                .totalAmount(totals.amount())
                .page(statementPage.getNumber())
                .size(statementPage.getSize())
                .totalElements(statementPage.getTotalElements())
                .totalPages(statementPage.getTotalPages())
                .tables(buildTableSummaries(allSessions, tableById, totalsBySession))
                .items(buildItemSummaries(salesRows))
                .days(buildDailySummaries(allSessions, totalsBySession, zone))
                .statements(statements)
                .build();
    }

    private Map<UUID, List<OrderResponse>> loadOrdersBySession(UUID restaurantId,
                                                               List<UUID> sessionIds,
                                                               UUID menuItemId) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        List<CustomerOrder> orders = customerOrderRepository
                .findAllByRestaurantIdAndSessionIdInOrderByCreatedAtDesc(restaurantId, sessionIds);
        List<UUID> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        Map<UUID, List<OrderItem>> itemsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findAllByOrderIdIn(orderIds).stream()
                        .filter(item -> restaurantId.equals(item.getRestaurantId()))
                        .filter(item -> menuItemId == null || menuItemId.equals(item.getMenuItemId()))
                        .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .filter(order -> menuItemId == null || itemsByOrderId.containsKey(order.getId()))
                .map(order -> orderMapper.toOrderResponse(
                        order,
                        itemsByOrderId.getOrDefault(order.getId(), List.of())
                ))
                .collect(Collectors.groupingBy(OrderResponse::sessionId));
    }

    private List<TableSalesSummary> buildTableSummaries(List<DiningSession> sessions,
                                                        Map<UUID, RestaurantTable> tableById,
                                                        Map<UUID, Totals> totalsBySession) {
        return sessions.stream()
                .collect(Collectors.groupingBy(DiningSession::getTableId))
                .entrySet()
                .stream()
                .map(entry -> {
                    Totals totals = sumTotals(entry.getValue(), totalsBySession);
                    return TableSalesSummary.builder()
                        .tableId(entry.getKey())
                        .tableNumber(tableNumber(tableById, entry.getKey()))
                        .settledBills(entry.getValue().size())
                        .totalOrders(totals.orders())
                        .totalItems(totals.items())
                        .totalAmount(totals.amount())
                        .build();
                })
                .sorted(Comparator.comparing(TableSalesSummary::totalAmount).reversed()
                        .thenComparing(TableSalesSummary::tableNumber))
                .toList();
    }

    private List<ItemSalesSummary> buildItemSummaries(List<OrderItemRepository.SalesRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(OrderItemRepository.SalesRow::getMenuItemId))
                .entrySet().stream()
                .map(entry -> new ItemSalesSummary(entry.getKey(), entry.getValue().get(0).getItemName(),
                        entry.getValue().stream().mapToLong(OrderItemRepository.SalesRow::getTotalItems).sum(),
                        entry.getValue().stream().map(OrderItemRepository.SalesRow::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(ItemSalesSummary::totalAmount).reversed()
                        .thenComparing(ItemSalesSummary::name))
                .toList();
    }

    private List<DailySalesSummary> buildDailySummaries(List<DiningSession> sessions,
                                                       Map<UUID, Totals> totalsBySession, ZoneId zone) {
        return sessions.stream().collect(Collectors.groupingBy(
                        session -> session.getClosedAt().atZoneSameInstant(zone).toLocalDate()))
                .entrySet().stream()
                .map(entry -> {
                    Totals totals = sumTotals(entry.getValue(), totalsBySession);
                    return new DailySalesSummary(entry.getKey(), totals.items(), totals.amount());
                })
                .sorted(Comparator.comparing(DailySalesSummary::date))
                .toList();
    }

    private Totals sumTotals(List<DiningSession> sessions, Map<UUID, Totals> totalsBySession) {
        return sessions.stream().map(session -> totalsBySession.get(session.getId()))
                .reduce(new Totals(0, 0, BigDecimal.ZERO), Totals::plus);
    }

    private record Totals(long orders, long items, BigDecimal amount) {
        Totals plus(Totals other) {
            return new Totals(orders + other.orders, items + other.items, amount.add(other.amount));
        }
    }

    private ZoneId reportZone(String timeZone) {
        try {
            return ZoneId.of(timeZone == null ? "UTC" : timeZone);
        } catch (DateTimeException exception) {
            throw new BadRequestException("Invalid report time zone. Use a name such as Asia/Kolkata or UTC.");
        }
    }

    private String tableNumber(Map<UUID, RestaurantTable> tableById, UUID tableId) {
        RestaurantTable table = tableById.get(tableId);
        return table != null ? table.getTableNumber() : tableId.toString().substring(0, 8);
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both from and to timestamps are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAX_REPORT_RANGE) > 0) {
            throw new BadRequestException("Sales reports are limited to a 366 day range");
        }
    }
}
