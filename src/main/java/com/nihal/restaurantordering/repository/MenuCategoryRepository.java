package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    List<MenuCategory> findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(UUID restaurantId);
}
