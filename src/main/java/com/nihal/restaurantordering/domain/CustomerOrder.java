package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_orders_table_id", columnList = "table_id"),
                @Index(name = "idx_orders_created_at", columnList = "created_at"),
                @Index(name = "idx_orders_session_id", columnList = "session_id"),
                @Index(name = "idx_orders_status", columnList = "status")
        }
)
public class CustomerOrder extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "customer_name", nullable = false, length = 80)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;
}
