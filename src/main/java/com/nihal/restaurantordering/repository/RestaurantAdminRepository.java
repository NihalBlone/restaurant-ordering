package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.RestaurantAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantAdminRepository extends JpaRepository<RestaurantAdmin, UUID> {

    Optional<RestaurantAdmin> findByUsernameNormalized(String usernameNormalized);

    Optional<RestaurantAdmin> findByIdAndActiveTrue(UUID id);

    boolean existsByUsernameNormalized(String usernameNormalized);
    boolean existsByEmailNormalized(String emailNormalized);
    List<RestaurantAdmin> findAllByRestaurantIdOrderByUsernameAsc(UUID restaurantId);
    long countByRestaurantId(UUID restaurantId);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RestaurantAdmin a set a.tokenVersion = a.tokenVersion + 1 where a.restaurantId = :restaurantId")
    int revokeRestaurantSessions(UUID restaurantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RestaurantAdmin a where a.id = :id")
    Optional<RestaurantAdmin> findForUpdate(UUID id);
}
