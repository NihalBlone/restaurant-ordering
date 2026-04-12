package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_keys_key", columnNames = "idempotency_key")
        }
)
public class IdempotencyKey {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String key;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
