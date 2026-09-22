package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    List<MenuCategory> findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(UUID restaurantId);

    List<MenuCategory> findAllByIdInAndRestaurantId(Collection<UUID> ids, UUID restaurantId);

    Optional<MenuCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(UUID restaurantId, String name, UUID id);

    @Query("select coalesce(max(category.displayOrder), 0) from MenuCategory category where category.restaurantId = :restaurantId")
    Integer findMaxDisplayOrder(@Param("restaurantId") UUID restaurantId);
}
