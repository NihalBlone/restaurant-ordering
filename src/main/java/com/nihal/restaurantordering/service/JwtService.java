package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.config.AuthProperties;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public String createAccessToken(RestaurantAdmin admin) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("qr-restaurant-ordering")
                .subject(admin.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(authProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES))
                .claim("username", admin.getUsername())
                .claim("role", admin.getRole().name())
                .claim("tokenVersion", admin.getTokenVersion());
        if (admin.getRestaurantId() != null) builder.claim("restaurantId", admin.getRestaurantId().toString());
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
