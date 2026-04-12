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
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_restaurant_id", columnList = "restaurant_id")
        }
)
public class OrderItem extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "menu_item_name_at_order_time", nullable = false, length = 120)
    private String menuItemNameAtOrderTime;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_order_time", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrderTime;
}
