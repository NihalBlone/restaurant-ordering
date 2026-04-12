package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @Query("""
            select o
            from CustomerOrder o
            where o.tableId = :tableId
              and o.createdAt > :sessionStart
            order by o.createdAt desc
            """)
    Page<CustomerOrder> findRecentOrdersByTableId(@Param("tableId") UUID tableId,
                                                  @Param("sessionStart") OffsetDateTime sessionStart,
                                                  Pageable pageable);

    Optional<CustomerOrder> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
