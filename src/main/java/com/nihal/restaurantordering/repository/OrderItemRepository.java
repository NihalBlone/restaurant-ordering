package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.OrderItem;
import com.nihal.restaurantordering.domain.DiningSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrderIdIn(Collection<UUID> orderIds);

    interface SalesRow {
        UUID getSessionId();
        UUID getMenuItemId();
        String getItemName();
        Long getTotalOrders();
        Long getTotalItems();
        BigDecimal getTotalAmount();
    }

    @Query("""
            select session.id as sessionId, item.menuItemId as menuItemId,
                   max(coalesce(menu.name, item.menuItemNameAtOrderTime)) as itemName,
                   count(distinct orders.id) as totalOrders,
                   sum(item.quantity) as totalItems,
                   sum(item.priceAtOrderTime * item.quantity) as totalAmount
            from OrderItem item
            join CustomerOrder orders on orders.id = item.orderId and orders.restaurantId = item.restaurantId
            join DiningSession session on session.id = orders.sessionId
                and session.restaurantId = orders.restaurantId and session.tableId = orders.tableId
            left join MenuItem menu on menu.id = item.menuItemId and menu.restaurantId = item.restaurantId
            where session.restaurantId = :restaurantId and session.status = :status
              and session.closedAt >= :from and session.closedAt <= :to
              and (:tableId is null or session.tableId = :tableId)
              and (:menuItemId is null or item.menuItemId = :menuItemId)
            group by session.id, item.menuItemId
            """)
    List<SalesRow> findSalesRows(@Param("restaurantId") UUID restaurantId,
                                @Param("status") DiningSessionStatus status,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                @Param("tableId") UUID tableId,
                                @Param("menuItemId") UUID menuItemId);
}
