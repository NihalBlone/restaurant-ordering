package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    interface MenuCategoryItemProjection {
        UUID getCategoryId();

        String getCategoryName();

        Integer getCategoryDisplayOrder();

        UUID getItemId();

        String getItemName();

        String getItemDescription();

        java.math.BigDecimal getItemPrice();

        String getItemImageUrl();

        Boolean getItemVegetarian();
    }

    interface AdminMenuItemProjection {
        UUID getItemId();

        UUID getCategoryId();

        String getCategoryName();

        Integer getCategoryDisplayOrder();

        String getItemName();

        String getItemDescription();

        java.math.BigDecimal getItemPrice();

        String getItemImageUrl();

        Boolean getItemVegetarian();

        Boolean getItemAvailable();
    }

    @Query("""
            select c.id as categoryId,
                   c.name as categoryName,
                   c.displayOrder as categoryDisplayOrder,
                   i.id as itemId,
                   i.name as itemName,
                   i.description as itemDescription,
                   i.price as itemPrice,
                   i.imageUrl as itemImageUrl,
                   i.vegetarian as itemVegetarian
            from MenuCategory c
            left join MenuItem i
                on i.categoryId = c.id
               and i.restaurantId = c.restaurantId
               and i.available = true
            where c.restaurantId = :restaurantId
            order by c.displayOrder asc, c.name asc, i.name asc
            """)
    List<MenuCategoryItemProjection> findMenuCategoryItemsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    @Query("""
            select i.id as itemId,
                   c.id as categoryId,
                   c.name as categoryName,
                   c.displayOrder as categoryDisplayOrder,
                   i.name as itemName,
                   i.description as itemDescription,
                   i.price as itemPrice,
                   i.imageUrl as itemImageUrl,
                   i.vegetarian as itemVegetarian,
                   i.available as itemAvailable
            from MenuItem i
            join MenuCategory c
              on c.id = i.categoryId
             and c.restaurantId = i.restaurantId
            where i.restaurantId = :restaurantId
            order by c.displayOrder asc, c.name asc, i.name asc
            """)
    List<AdminMenuItemProjection> findAdminMenuItemsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    long countByCategoryIdAndRestaurantId(UUID categoryId, UUID restaurantId);

    List<MenuItem> findAllByRestaurantIdAndAvailableTrueOrderByNameAsc(UUID restaurantId);

    List<MenuItem> findAllByIdInAndRestaurantIdAndAvailableTrue(Collection<UUID> ids, UUID restaurantId);
}
