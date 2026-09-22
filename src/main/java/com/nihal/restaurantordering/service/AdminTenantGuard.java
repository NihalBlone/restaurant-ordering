package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.exception.ForbiddenException;
import com.nihal.restaurantordering.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AdminTenantGuard {

    public UUID restaurantId(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString("restaurantId") == null) {
            throw new UnauthorizedException("Restaurant login is required");
        }
        return UUID.fromString(jwt.getClaimAsString("restaurantId"));
    }

    public UUID adminId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new UnauthorizedException("Restaurant login is required");
        }
        return UUID.fromString(jwt.getSubject());
    }

    public UUID requireRestaurant(Jwt jwt, UUID requestedRestaurantId) {
        UUID authenticatedRestaurantId = restaurantId(jwt);
        if (!authenticatedRestaurantId.equals(requestedRestaurantId)) {
            throw new ForbiddenException("You cannot access another restaurant's data");
        }
        return authenticatedRestaurantId;
    }
}
