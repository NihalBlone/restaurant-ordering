package com.nihal.restaurantordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "audit_entries") @Getter @Setter
public class AuditEntry extends BaseEntity {
    @Column(name="actor_id") private UUID actorId;
    @Column(name="actor_name", nullable=false, length=80) private String actorName;
    @Column(name="restaurant_id") private UUID restaurantId;
    @Column(nullable=false, length=80) private String action;
    @Column(name="subject_id") private UUID subjectId;
    @Column(nullable=false, length=2000) private String details;
}
