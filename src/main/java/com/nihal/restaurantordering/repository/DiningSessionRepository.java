package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.DiningSession;
import com.nihal.restaurantordering.domain.DiningSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningSessionRepository extends JpaRepository<DiningSession, UUID> {

    Optional<DiningSession> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query("""
            select session
            from DiningSession session
            where session.restaurantId = :restaurantId
              and session.status = :status
              and session.closedAt >= :from
              and session.closedAt <= :to
              and (:tableId is null or session.tableId = :tableId)
              and (:menuItemId is null or exists (
                  select item.id from OrderItem item join CustomerOrder orders on orders.id = item.orderId
                  where orders.sessionId = session.id and orders.restaurantId = session.restaurantId
                    and orders.tableId = session.tableId and item.restaurantId = session.restaurantId
                    and item.menuItemId = :menuItemId
              ))
            order by session.closedAt desc, session.id desc
            """)
    Page<DiningSession> findStatementPage(@Param("restaurantId") UUID restaurantId,
                                          @Param("status") DiningSessionStatus status,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to,
                                          @Param("tableId") UUID tableId,
                                          @Param("menuItemId") UUID menuItemId,
                                          Pageable pageable);

    @Query("""
            select session
            from DiningSession session
            where session.restaurantId = :restaurantId
              and session.status = :status
              and session.closedAt >= :from
              and session.closedAt <= :to
              and (:tableId is null or session.tableId = :tableId)
              and (:menuItemId is null or exists (
                  select item.id from OrderItem item join CustomerOrder orders on orders.id = item.orderId
                  where orders.sessionId = session.id and orders.restaurantId = session.restaurantId
                    and orders.tableId = session.tableId and item.restaurantId = session.restaurantId
                    and item.menuItemId = :menuItemId
              ))
            """)
    List<DiningSession> findAllForTotals(@Param("restaurantId") UUID restaurantId,
                                         @Param("status") DiningSessionStatus status,
                                         @Param("from") OffsetDateTime from,
                                         @Param("to") OffsetDateTime to,
                                         @Param("tableId") UUID tableId,
                                         @Param("menuItemId") UUID menuItemId);
}
