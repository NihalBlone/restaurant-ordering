package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "menu_items",
        indexes = {
                @Index(name = "idx_menu_items_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_menu_items_category_id", columnList = "category_id")
        }
)
public class MenuItem extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "is_vegetarian", nullable = false)
    private boolean vegetarian = true;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;
}
