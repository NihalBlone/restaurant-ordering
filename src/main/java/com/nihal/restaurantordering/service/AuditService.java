package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.AuditEntry;
import com.nihal.restaurantordering.repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository repository;

    @Transactional
    public void record(String action, UUID restaurantId, UUID subjectId, String details) {
        var entry = new AuditEntry();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            entry.setActorId(UUID.fromString(jwt.getSubject()));
            entry.setActorName(jwt.getClaimAsString("username"));
        } else {
            entry.setActorName("system");
        }
        entry.setAction(action);
        entry.setRestaurantId(restaurantId);
        entry.setSubjectId(subjectId);
        entry.setDetails(details);
        repository.save(entry);
    }
}
