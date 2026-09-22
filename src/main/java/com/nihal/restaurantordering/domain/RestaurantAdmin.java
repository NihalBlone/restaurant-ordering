package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "restaurant_admins",
        indexes = {
                @Index(name = "idx_restaurant_admins_restaurant_id", columnList = "restaurant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_restaurant_admin_username", columnNames = "username_normalized"),
                @UniqueConstraint(name = "uk_restaurant_admin_email", columnNames = "email_normalized")
        }
)
public class RestaurantAdmin extends BaseEntity {

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "username_normalized", nullable = false, length = 80)
    private String usernameNormalized;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminRole role = AdminRole.RESTAURANT_ADMIN;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "last_totp_step", nullable = false)
    private long lastTotpStep = -1;
}
