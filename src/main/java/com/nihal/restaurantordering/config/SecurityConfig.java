package com.nihal.restaurantordering.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BearerTokenResolver bearerTokenResolver) throws Exception {
        var csrfRepository = org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(authProperties.isSecureCookie()).sameSite("Strict"));
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        .withObjectPostProcessor(new org.springframework.security.config.annotation.ObjectPostProcessor<org.springframework.security.web.csrf.CsrfFilter>() {
                            @Override
                            public <O extends org.springframework.security.web.csrf.CsrfFilter> O postProcess(O filter) {
                                // Resource Server normally exempts bearer requests; our bearer tokens are cookies.
                                filter.setRequireCsrfProtectionMatcher(request ->
                                        org.springframework.security.web.csrf.CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                                        && !request.getServletPath().startsWith("/ws-orders/"));
                                return filter;
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/platform-login", "/api/auth/csrf", "/api/auth/logout",
                                "/api/auth/password-reset/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menu", "/api/orders", "/uploads/**",
                                "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers("/ws-orders/**", "/error").permitAll()
                        .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/admin/staff/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers("/api/admin/reports/**").hasAnyRole("RESTAURANT_ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/admin/tables", "/api/admin/notice").hasAnyRole("RESTAURANT_ADMIN", "MANAGER", "WAITER", "KITCHEN")
                        .requestMatchers("/api/admin/tables/**", "/api/restaurants/**").hasAnyRole("RESTAURANT_ADMIN", "MANAGER")
                        .requestMatchers("/api/table-sessions/**").hasAnyRole("RESTAURANT_ADMIN", "MANAGER", "WAITER")
                        .requestMatchers(HttpMethod.PUT, "/api/orders/*/status").hasAnyRole("RESTAURANT_ADMIN", "MANAGER", "WAITER", "KITCHEN")
                        .requestMatchers("/api/auth/me").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> {
                            var converter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
                            converter.setJwtGrantedAuthoritiesConverter(token -> java.util.List.of(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + token.getClaimAsString("role"))));
                            jwt.jwtAuthenticationConverter(converter);
                        })
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "Restaurant login is required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "You do not have access to this resource", request.getRequestURI()))
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "Sign in to continue", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "Access denied or security token missing. Refresh and try again.", request.getRequestURI())))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret()));
    }

    @Bean
    public JwtDecoder jwtDecoder(com.nihal.restaurantordering.service.AccountTokenValidator accountValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecret())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("qr-restaurant-ordering"), accountValidator));
        return decoder;
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return request -> {
            String path = request.getServletPath();
            // A stale cookie must not prevent signing in again or using a public QR.
            if (path.equals("/api/auth/login") || path.equals("/api/auth/platform-login")
                    || path.equals("/api/auth/csrf") || path.startsWith("/api/auth/password-reset/")
                    || path.equals("/api/menu") || path.equals("/api/orders")) {
                return null;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            return Arrays.stream(cookies)
                    .filter(cookie -> authProperties.getCookieName().equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);
        };
    }

    private SecretKey jwtSecret() {
        byte[] secretBytes = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret must contain at least 32 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    private void writeSecurityError(jakarta.servlet.http.HttpServletResponse response,
                                    int status,
                                    String message,
                                    String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status);
        body.put("error", status == 401 ? "Unauthorized" : "Forbidden");
        body.put("message", message);
        body.put("path", path);
        body.put("details", java.util.List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
