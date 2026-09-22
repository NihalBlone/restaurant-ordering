package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.RestaurantTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {
    long countByRestaurantId(UUID restaurantId);

    Optional<RestaurantTable> findByIdAndActiveTrue(UUID id);

    Optional<RestaurantTable> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select table from RestaurantTable table where table.id = :id and table.active = true")
    Optional<RestaurantTable> findActiveByIdForUpdate(@Param("id") UUID id);

    List<RestaurantTable> findAllByRestaurantIdOrderByTableNumberAsc(UUID restaurantId);

    boolean existsByRestaurantIdAndTableNumberIgnoreCase(UUID restaurantId, String tableNumber);
}
