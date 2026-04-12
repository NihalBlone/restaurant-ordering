package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    Optional<RestaurantTable> findByIdAndActiveTrue(UUID id);

    List<RestaurantTable> findAllByRestaurantIdOrderByTableNumberAsc(UUID restaurantId);
}
