package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrderIdIn(Collection<UUID> orderIds);
}
