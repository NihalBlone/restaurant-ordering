package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "restaurant_tables",
        indexes = {
                @Index(name = "idx_restaurant_tables_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_restaurant_tables_is_active", columnList = "is_active"),
                @Index(name = "idx_restaurant_tables_current_session_id", columnList = "current_session_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_restaurant_table_number", columnNames = {"restaurant_id", "table_number"})
        }
)
public class RestaurantTable extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_number", nullable = false, length = 30)
    private String tableNumber;

    @Column(name = "qr_code_url", nullable = false, length = 500)
    private String qrCodeUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "current_session_id")
    private UUID currentSessionId;

    @Column(name = "last_session_closed_at")
    private OffsetDateTime lastSessionClosedAt;

    @Version
    private Long version;
}
