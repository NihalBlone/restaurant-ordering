package com.nihal.restaurantordering.dto.platform;

import com.nihal.restaurantordering.domain.AdminRole;
import com.nihal.restaurantordering.domain.RestaurantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PlatformDtos {
    private PlatformDtos() {}

    public record RestaurantInput(
            @NotBlank @Size(max=120) String name,
            @NotBlank @Size(max=255) String location,
            @NotBlank @Pattern(regexp="[A-Z0-9_-]{1,30}") String planCode,
            @Min(1) @Max(1000) int tableLimit,
            @Min(1) @Max(500) int staffLimit,
            OffsetDateTime trialEndsAt) {}

    public record OnboardRequest(@NotNull @Valid RestaurantInput restaurant,
                                 @NotNull @Valid InviteRequest owner) {}

    public record InviteRequest(
            @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,80}") String username,
            @NotBlank @Email @Size(max=254) String email,
            @NotNull AdminRole role) {}

    public record RestaurantView(UUID id, String name, String location, RestaurantStatus status,
                                 String planCode, int tableLimit, int staffLimit, long tableCount,
                                 long staffCount, OffsetDateTime trialEndsAt, OffsetDateTime createdAt) {}
    public record StaffView(UUID id, String username, String email, AdminRole role, boolean active) {}
    public record InviteResult(StaffView account, String developmentResetToken) {}
    public record OnboardResult(RestaurantView restaurant, InviteResult invitation) {}
    public record StaffUpdate(@NotNull AdminRole role, @NotNull Boolean active) {}
    public record StatusUpdate(@NotNull RestaurantStatus status, @NotBlank @Size(max=500) String reason) {}
    public record SettingsInput(@NotBlank @Size(max=120) String productName,
                                @Email @Size(max=254) String supportEmail,
                                @NotNull @Size(max=1000) String announcement,
                                @NotNull Long version) {}
    public record SettingsView(String productName, String supportEmail, String announcement,
                               OffsetDateTime updatedAt, Long version) {}
    public record AuditView(UUID id, String actorName, UUID restaurantId, String action,
                            UUID subjectId, String details, OffsetDateTime createdAt) {}
    public record PageView<T>(List<T> content, int page, int totalPages, long totalElements) {
        public static <T> PageView<T> of(org.springframework.data.domain.Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
        }
    }
}
