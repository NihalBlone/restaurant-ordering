package com.nihal.restaurantordering.domain;

import jakarta.persistence.Column;
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
        name = "menu_categories",
        indexes = {
                @Index(name = "idx_menu_categories_restaurant_id", columnList = "restaurant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_category_name", columnNames = {"restaurant_id", "name"})
        }
)
public class MenuCategory extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
