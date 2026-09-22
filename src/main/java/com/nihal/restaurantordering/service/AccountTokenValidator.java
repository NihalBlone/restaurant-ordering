package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.AdminRole;
import com.nihal.restaurantordering.domain.RestaurantStatus;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final RestaurantAdminRepository accounts;
    private final RestaurantRepository restaurants;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            var account = accounts.findByIdAndActiveTrue(UUID.fromString(token.getSubject())).orElse(null);
            Number version = token.getClaim("tokenVersion");
            if (account != null && version != null && account.getTokenVersion() == version.longValue()
                    && account.getRole().name().equals(token.getClaimAsString("role"))
                    && Objects.equals(account.getRestaurantId() == null ? null : account.getRestaurantId().toString(),
                                      token.getClaimAsString("restaurantId"))
                    && (account.getRole() == AdminRole.PLATFORM_ADMIN
                        || restaurants.findById(account.getRestaurantId())
                            .map(r -> r.getStatus() == RestaurantStatus.ACTIVE).orElse(false))) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed subjects fail closed just like revoked accounts.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session expired or access revoked", null));
    }
}
