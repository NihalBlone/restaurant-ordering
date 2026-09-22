package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "restaurants")
public class Restaurant extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Column(name = "plan_code", nullable = false, length = 30)
    private String planCode = "STARTER";

    @Column(name = "table_limit", nullable = false)
    private int tableLimit = 20;

    @Column(name = "staff_limit", nullable = false)
    private int staffLimit = 10;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;
}
