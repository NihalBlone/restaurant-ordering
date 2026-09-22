package com.nihal.restaurantordering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret;
    private long accessTokenMinutes = 480;
    private String cookieName = "restaurant_access_token";
    private boolean secureCookie;
    private long passwordResetMinutes = 30;
    private boolean exposeResetToken;
}
