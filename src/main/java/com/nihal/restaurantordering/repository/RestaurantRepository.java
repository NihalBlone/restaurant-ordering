package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Restaurant r where r.id = :id")
    java.util.Optional<Restaurant> findForUpdate(UUID id);

    org.springframework.data.domain.Page<Restaurant> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String name, org.springframework.data.domain.Pageable pageable);

    long countByStatus(com.nihal.restaurantordering.domain.RestaurantStatus status);
}
