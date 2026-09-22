package com.nihal.restaurantordering.controller;

import com.nihal.restaurantordering.config.AuthProperties;
import com.nihal.restaurantordering.dto.auth.AuthSessionResponse;
import com.nihal.restaurantordering.dto.auth.LoginRequest;
import com.nihal.restaurantordering.dto.auth.PasswordResetConfirmRequest;
import com.nihal.restaurantordering.dto.auth.PasswordResetRequest;
import com.nihal.restaurantordering.dto.auth.PasswordResetRequestResponse;
import com.nihal.restaurantordering.service.AdminTenantGuard;
import com.nihal.restaurantordering.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final AdminTenantGuard adminTenantGuard;

    @GetMapping("/csrf")
    public Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    public record PlatformLoginRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String username,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 72) String password,
            @jakarta.validation.constraints.Pattern(regexp = "^$|[0-9]{6}") String code) {}

    @PostMapping("/platform-login")
    public AuthSessionResponse platformLogin(@Valid @RequestBody PlatformLoginRequest request, HttpServletResponse response) {
        var result = authService.platformLogin(request.username(), request.password(), request.code());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(result.token()).toString());
        return result.session();
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(result.token()).toString());
        return result.session();
    }

    @GetMapping("/me")
    public AuthSessionResponse currentSession(@AuthenticationPrincipal Jwt jwt) {
        return authService.getSession(adminTenantGuard.adminId(jwt));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response, @AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) authService.revoke(adminTenantGuard.adminId(jwt));
        response.addHeader(HttpHeaders.SET_COOKIE, clearAccessCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public PasswordResetRequestResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        return authService.requestPasswordReset(request.username());
    }

    @PostMapping("/password-reset/confirm")
    public Map<String, String> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        authService.resetPassword(request.token(), request.newPassword());
        return Map.of("message", "Password updated. You can now sign in.");
    }

    private ResponseCookie accessCookie(String token) {
        return ResponseCookie.from(authProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(authProperties.getAccessTokenMinutes()))
                .build();
    }

    private ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(authProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
