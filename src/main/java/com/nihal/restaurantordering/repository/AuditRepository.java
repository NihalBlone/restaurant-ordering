package com.nihal.restaurantordering.repository;

import com.nihal.restaurantordering.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    @Query("select a from AuditEntry a where (:restaurantId is null or a.restaurantId = :restaurantId) order by a.createdAt desc, a.id desc")
    Page<AuditEntry> search(UUID restaurantId, Pageable pageable);
}
