package com.nihal.restaurantordering.bootstrap;

import com.nihal.restaurantordering.config.AuthProperties;
import com.nihal.restaurantordering.config.PlatformProperties;
import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import com.nihal.restaurantordering.service.PlatformTotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PlatformBootstrap implements ApplicationRunner {
    private final PlatformProperties properties;
    private final AuthProperties auth;
    private final Environment environment;
    private final RestaurantAdminRepository accounts;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (environment.matchesProfiles("prod")) {
            if (!auth.isSecureCookie() || auth.isExposeResetToken()
                    || auth.getJwtSecret().contains("local-dev") || auth.getJwtSecret().length() < 48
                    || environment.getProperty("app.sample.enabled", Boolean.class, false)
                    || properties.getTotpSecret().isBlank()
                    || !environment.getRequiredProperty("app.customer-base-url").startsWith("https://")) {
                throw new IllegalStateException("Production requires HTTPS, secure cookies, a strong JWT secret, platform TOTP, and no demo data/reset-token exposure");
            }
        }
        if (!properties.getTotpSecret().isBlank()) PlatformTotpService.code(properties.getTotpSecret(), 0);
        if (properties.getBootstrapUsername().isBlank()) return;
        String username = properties.getBootstrapUsername().trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._-]{3,80}")) {
            throw new IllegalStateException("APP_PLATFORM_USERNAME must be 3-80 characters using only letters, numbers, dots, underscores, or hyphens");
        }
        var existing = accounts.findByUsernameNormalized(username);
        if (existing.isPresent()) {
            if (existing.get().getRole() != AdminRole.PLATFORM_ADMIN) {
                throw new IllegalStateException("Platform bootstrap username already belongs to a restaurant account");
            }
            return;
        }
        String password = properties.getBootstrapPassword();
        String email = properties.getBootstrapEmail().trim();
        if (email.isBlank()) {
            throw new IllegalStateException("APP_PLATFORM_EMAIL is missing. Set it in the same terminal that starts Maven, or run bash scripts/start-local-platform.sh");
        }
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("APP_PLATFORM_EMAIL must be a valid email address of at most 254 characters");
        }
        if (password.isBlank()) {
            throw new IllegalStateException("APP_PLATFORM_PASSWORD is missing. Set a unique 14-72 character password in the same terminal that starts Maven, or run bash scripts/start-local-platform.sh");
        }
        if (password.length() < 14 || password.length() > 72
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("APP_PLATFORM_PASSWORD must be 14-72 characters and at most 72 UTF-8 bytes. The restaurant demo password is too short for platform bootstrap.");
        }
        var admin = new RestaurantAdmin();
        admin.setUsername(username);
        admin.setUsernameNormalized(username);
        admin.setEmail(email);
        admin.setEmailNormalized(admin.getEmail().toLowerCase(Locale.ROOT));
        admin.setRole(AdminRole.PLATFORM_ADMIN);
        admin.setPasswordHash(encoder.encode(password));
        accounts.save(admin);
    }
}
