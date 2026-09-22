package com.nihal.restaurantordering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties(prefix="app.platform") @Getter @Setter
public class PlatformProperties {
    private String bootstrapUsername = "";
    private String bootstrapEmail = "";
    private String bootstrapPassword = "";
    private String totpSecret = "";
}
