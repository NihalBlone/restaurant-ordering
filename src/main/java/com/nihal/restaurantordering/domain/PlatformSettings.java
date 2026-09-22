package com.nihal.restaurantordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Table(name="platform_settings") @Getter @Setter
public class PlatformSettings {
    @Id private Integer id;
    @Column(name="product_name", nullable=false, length=120) private String productName;
    @Column(name="support_email", nullable=false, length=254) private String supportEmail;
    @Column(nullable=false, length=1000) private String announcement;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    @Version private Long version;
}
