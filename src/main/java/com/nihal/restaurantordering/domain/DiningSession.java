package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "dining_sessions",
        indexes = {
                @Index(name = "idx_dining_sessions_restaurant_closed", columnList = "restaurant_id, closed_at"),
                @Index(name = "idx_dining_sessions_table_closed", columnList = "table_id, closed_at"),
                @Index(name = "idx_dining_sessions_status", columnList = "status")
        }
)
public class DiningSession {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiningSessionStatus status;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "total_orders", nullable = false)
    private int totalOrders;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
}
