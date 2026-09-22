package com.nihal.restaurantordering.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String RESTAURANT_TOPIC_PREFIX = "/topic/restaurant/";

    private final WebSocketProperties webSocketProperties;
    private final com.nihal.restaurantordering.service.AccountTokenValidator accountValidator;
    private final java.util.concurrent.ConcurrentHashMap<String, Jwt> authenticatedSessions = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-orders")
                .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (accessor.getMessageType() == SimpMessageType.MESSAGE) {
                    throw new AccessDeniedException("Clients cannot publish order events");
                }
                if (accessor.getMessageType() == SimpMessageType.SUBSCRIBE) {
                    String destination = accessor.getDestination();
                    if (destination == null || !destination.matches("/topic/(restaurant|table)/[a-fA-F0-9-]{36}")) {
                        throw new AccessDeniedException("Invalid subscription destination");
                    }
                    authorizeRestaurantSubscription(accessor);
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var accessor = StompHeaderAccessor.wrap(message);
                if (accessor.getDestination() != null && accessor.getDestination().startsWith(RESTAURANT_TOPIC_PREFIX)) {
                    Jwt jwt = authenticatedSessions.get(accessor.getSessionId());
                    if (jwt == null || jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(java.time.Instant.now())
                            || accountValidator.validate(jwt).hasErrors()) return null;
                }
                return message;
            }
        });
    }

    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        authenticatedSessions.remove(event.getSessionId());
    }

    private void authorizeRestaurantSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(RESTAURANT_TOPIC_PREFIX)) {
            return;
        }

        UUID requestedRestaurantId;
        try {
            requestedRestaurantId = UUID.fromString(destination.substring(RESTAURANT_TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid restaurant topic");
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Restaurant login is required");
        }

        String restaurantClaim = jwt.getClaimAsString("restaurantId");
        if (restaurantClaim == null || !requestedRestaurantId.equals(UUID.fromString(restaurantClaim))
                || accountValidator.validate(jwt).hasErrors() || jwt.getExpiresAt() == null
                || jwt.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new AccessDeniedException("You cannot subscribe to another restaurant's orders");
        }
        authenticatedSessions.put(accessor.getSessionId(), jwt);
    }
}
