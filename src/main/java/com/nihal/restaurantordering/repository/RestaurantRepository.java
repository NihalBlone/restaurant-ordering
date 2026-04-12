package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
}
