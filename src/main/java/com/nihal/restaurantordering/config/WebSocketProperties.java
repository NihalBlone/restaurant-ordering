package com.nihal.restaurantordering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private String[] allowedOrigins = new String[]{"*"};
}
