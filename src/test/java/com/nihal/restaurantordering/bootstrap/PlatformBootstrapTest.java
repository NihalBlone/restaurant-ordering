package com.nihal.restaurantordering.bootstrap;

import com.nihal.restaurantordering.config.AuthProperties;
import com.nihal.restaurantordering.config.PlatformProperties;
import com.nihal.restaurantordering.domain.AdminRole;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformBootstrapTest {
    @Mock private Environment environment;
    @Mock private RestaurantAdminRepository accounts;
    @Mock private PasswordEncoder encoder;
    private PlatformProperties properties;
    private PlatformBootstrap bootstrap;

    @BeforeEach
    void setup() {
        properties = new PlatformProperties();
        properties.setBootstrapUsername("owner");
        properties.setBootstrapEmail("owner@example.com");
        properties.setBootstrapPassword("A-unique-test-password!42");
        bootstrap = new PlatformBootstrap(properties, new AuthProperties(), environment, accounts, encoder);
    }

    @Test
    void identifiesMissingEmailWithoutExposingPassword() {
        properties.setBootstrapEmail("");
        assertThatThrownBy(this::run).hasMessageContaining("APP_PLATFORM_EMAIL is missing")
                .hasMessageNotContaining(properties.getBootstrapPassword());
        verify(accounts, never()).save(any());
    }

    @Test
    void identifiesMalformedEmail() {
        properties.setBootstrapEmail("not-an-email");
        assertThatThrownBy(this::run).hasMessageContaining("APP_PLATFORM_EMAIL must be a valid email");
    }

    @Test
    void identifiesMissingPassword() {
        properties.setBootstrapPassword("");
        assertThatThrownBy(this::run).hasMessageContaining("APP_PLATFORM_PASSWORD is missing");
    }

    @Test
    void rejectsRestaurantDemoPasswordForPlatformBootstrap() {
        properties.setBootstrapPassword("Admin@12345");
        assertThatThrownBy(this::run).hasMessageContaining("APP_PLATFORM_PASSWORD must be 14-72 characters")
                .hasMessageNotContaining("Admin@12345");
    }

    @Test
    void rejectsPasswordsThatBcryptWouldTruncate() {
        properties.setBootstrapPassword("\u00e9".repeat(40));
        assertThatThrownBy(this::run).hasMessageContaining("at most 72 UTF-8 bytes");
    }

    @Test
    void identifiesInvalidUsernameSeparately() {
        properties.setBootstrapUsername("owner with spaces");
        assertThatThrownBy(this::run).hasMessageContaining("APP_PLATFORM_USERNAME must be");
        verifyNoInteractions(accounts, encoder);
    }

    @Test
    void createsOwnerAndTrimsEmail() {
        properties.setBootstrapEmail("  Owner@example.com  ");
        when(encoder.encode(properties.getBootstrapPassword())).thenReturn("hashed-password");
        run();
        var captured = ArgumentCaptor.forClass(RestaurantAdmin.class);
        verify(accounts).save(captured.capture());
        assertThat(captured.getValue().getEmail()).isEqualTo("Owner@example.com");
        assertThat(captured.getValue().getEmailNormalized()).isEqualTo("owner@example.com");
        assertThat(captured.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(captured.getValue().getRole()).isEqualTo(AdminRole.PLATFORM_ADMIN);
        assertThat(captured.getValue().getRestaurantId()).isNull();
    }

    @Test
    void existingOwnerDoesNotRequireOrOverwriteBootstrapPassword() {
        var existing = new RestaurantAdmin();
        existing.setRole(AdminRole.PLATFORM_ADMIN);
        existing.setPasswordHash("original-hash");
        when(accounts.findByUsernameNormalized("owner")).thenReturn(Optional.of(existing));
        properties.setBootstrapEmail("");
        properties.setBootstrapPassword("");
        run();
        verify(accounts, never()).save(any());
        verifyNoInteractions(encoder);
        assertThat(existing.getPasswordHash()).isEqualTo("original-hash");
    }

    private void run() {
        bootstrap.run(new DefaultApplicationArguments());
    }
}
