package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.config.AuthProperties;
import com.nihal.restaurantordering.domain.PasswordResetToken;
import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.dto.auth.AuthSessionResponse;
import com.nihal.restaurantordering.dto.auth.PasswordResetRequestResponse;
import com.nihal.restaurantordering.exception.BadRequestException;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.exception.UnauthorizedException;
import com.nihal.restaurantordering.repository.PasswordResetTokenRepository;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final RestaurantAdminRepository restaurantAdminRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final PlatformTotpService totp;
    private final ResetMailService resetMail;
    private final AuthAttemptLimiter attempts;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public LoginResult login(String username, String password) {
        return authenticate(username, password, false, null);
    }

    @Transactional
    public LoginResult platformLogin(String username, String password, String code) {
        return authenticate(username, password, true, code);
    }

    private LoginResult authenticate(String username, String password, boolean platform, String code) {
        String normalizedUsername = normalize(username);
        attempts.check("login:" + normalizedUsername, 20);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UnauthorizedException("Invalid username or password");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedUsername, password)
            );
        } catch (AuthenticationException exception) {
            log.warn("Restaurant admin login rejected. username={}", normalizedUsername);
            throw new UnauthorizedException("Invalid username or password");
        }

        RestaurantAdmin admin = restaurantAdminRepository
                .findByUsernameNormalized(normalizedUsername)
                .filter(RestaurantAdmin::isActive)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        admin = restaurantAdminRepository.findForUpdate(admin.getId()).orElseThrow();
        // Authentication loaded this entity before the lock; refresh to observe concurrent resets/MFA use.
        entityManager.refresh(admin, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!admin.isActive() || !passwordEncoder.matches(password, admin.getPasswordHash())
                || platform != (admin.getRole() == com.nihal.restaurantordering.domain.AdminRole.PLATFORM_ADMIN)) {
            throw new UnauthorizedException("Invalid credentials for this portal");
        }
        if (platform) {
            totp.verify(admin, code);
        } else if (restaurantRepository.findById(admin.getRestaurantId())
                .map(r -> r.getStatus() != com.nihal.restaurantordering.domain.RestaurantStatus.ACTIVE).orElse(true)) {
            throw new UnauthorizedException("Restaurant access is suspended. Contact the platform owner.");
        }
        log.info("Restaurant admin logged in. adminId={} restaurantId={}",
                admin.getId(), admin.getRestaurantId());
        return new LoginResult(jwtService.createAccessToken(admin), toSession(admin));
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse getSession(UUID adminId) {
        RestaurantAdmin admin = restaurantAdminRepository.findByIdAndActiveTrue(adminId)
                .orElseThrow(() -> new UnauthorizedException("Restaurant login has expired"));
        return toSession(admin);
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(String username) {
        String normalizedUsername = normalize(username);
        attempts.check("reset:" + normalizedUsername, 5);
        RestaurantAdmin admin = restaurantAdminRepository
                .findByUsernameNormalized(normalizedUsername)
                .filter(RestaurantAdmin::isActive)
                .orElse(null);

        if (admin == null) {
            log.warn("Password reset requested for unknown username={}", normalizedUsername);
            return resetResponse(null);
        }

        admin = restaurantAdminRepository.findForUpdate(admin.getId()).orElseThrow();
        entityManager.refresh(admin, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!admin.isActive()) return resetResponse(null);

        passwordResetTokenRepository.deleteAllByAdminIdAndUsedAtIsNull(admin.getId());
        String rawToken = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setAdminId(admin.getId());
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(authProperties.getPasswordResetMinutes()));
        passwordResetTokenRepository.save(resetToken);
        resetMail.send(admin.getEmail(), rawToken);
        log.info("Password reset token created. adminId={} expiresAt={}",
                admin.getId(), resetToken.getExpiresAt());
        return resetResponse(rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must be at most 72 UTF-8 bytes; use fewer characters");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = hashToken(rawToken.trim());
        var candidate = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Password reset link is invalid or expired"));
        // Always lock account before token, matching reset issuance and avoiding lock-order inversions.
        RestaurantAdmin admin = restaurantAdminRepository.findForUpdate(candidate.getAdminId())
                .filter(RestaurantAdmin::isActive)
                .orElseThrow(() -> new NotFoundException("Restaurant administrator no longer exists"));
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hash, now)
                .orElseThrow(() -> new BadRequestException("Password reset link is invalid or expired"));
        entityManager.refresh(resetToken, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("Password reset link is invalid or expired");
        }

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        resetToken.setUsedAt(now);
        log.info("Restaurant admin password reset. adminId={} restaurantId={}",
                admin.getId(), admin.getRestaurantId());
    }

    private AuthSessionResponse toSession(RestaurantAdmin admin) {
        Restaurant restaurant = admin.getRestaurantId() == null ? null : restaurantRepository.findById(admin.getRestaurantId())
                .orElseThrow(() -> new NotFoundException("Restaurant account not found"));
        return AuthSessionResponse.builder()
                .adminId(admin.getId())
                .restaurantId(admin.getRestaurantId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .restaurantName(restaurant == null ? null : restaurant.getName())
                .restaurantLocation(restaurant == null ? null : restaurant.getLocation())
                .role(admin.getRole().name())
                .build();
    }

    @Transactional
    public void revoke(UUID adminId) {
        var admin = restaurantAdminRepository.findForUpdate(adminId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        admin.setTokenVersion(admin.getTokenVersion() + 1);
    }

    private PasswordResetRequestResponse resetResponse(String rawToken) {
        return PasswordResetRequestResponse.builder()
                .message("If the username exists, a password reset link has been issued")
                .developmentResetToken(authProperties.isExposeResetToken() ? rawToken : null)
                .build();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record LoginResult(String token, AuthSessionResponse session) {
    }
}
