package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.*;
import com.nihal.restaurantordering.dto.platform.PlatformDtos.*;
import com.nihal.restaurantordering.exception.*;
import com.nihal.restaurantordering.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffService {
    private final RestaurantAdminRepository accounts;
    private final RestaurantRepository restaurants;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final AuditService audit;

    @Transactional(readOnly=true)
    public List<StaffView> list(UUID restaurantId) {
        return accounts.findAllByRestaurantIdOrderByUsernameAsc(restaurantId).stream().map(this::view).toList();
    }

    @Transactional
    public InviteResult invite(UUID restaurantId, InviteRequest request) {
        requireRestaurantRole(request.role());
        var restaurant = restaurants.findForUpdate(restaurantId).orElseThrow(() -> new NotFoundException("Restaurant not found"));
        if (accounts.countByRestaurantId(restaurantId) >= restaurant.getStaffLimit()) {
            throw new ConflictException("Staff account limit reached. Contact the platform owner.");
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accounts.findByUsernameNormalized(username).isPresent() || accounts.existsByEmailNormalized(email)) {
            throw new ConflictException("This username or email is already registered");
        }
        var account = new RestaurantAdmin();
        account.setRestaurantId(restaurantId);
        account.setUsername(request.username().trim());
        account.setUsernameNormalized(username);
        account.setEmail(request.email().trim());
        account.setEmailNormalized(email);
        account.setRole(request.role());
        account.setPasswordHash(encoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
        accounts.saveAndFlush(account);
        var invitation = auth.requestPasswordReset(username);
        audit.record("STAFF_INVITED", restaurantId, account.getId(), "Role: " + request.role());
        return new InviteResult(view(account), invitation.developmentResetToken());
    }

    @Transactional
    public StaffView update(UUID restaurantId, UUID accountId, StaffUpdate request) {
        requireRestaurantRole(request.role());
        restaurants.findForUpdate(restaurantId).orElseThrow(() -> new NotFoundException("Restaurant not found"));
        var account = scoped(restaurantId, accountId);
        if (account.isActive() && account.getRole() == AdminRole.RESTAURANT_ADMIN
                && (!request.active() || request.role() != AdminRole.RESTAURANT_ADMIN)
                && accounts.findAllByRestaurantIdOrderByUsernameAsc(restaurantId).stream()
                    .filter(a -> a.isActive() && a.getRole() == AdminRole.RESTAURANT_ADMIN).count() <= 1) {
            throw new ConflictException("Keep at least one active restaurant owner before changing this account");
        }
        account.setRole(request.role());
        account.setActive(request.active());
        account.setTokenVersion(account.getTokenVersion() + 1);
        audit.record("STAFF_UPDATED", restaurantId, accountId, "Role: " + request.role() + "; active: " + request.active());
        return view(account);
    }

    @Transactional
    public void revoke(UUID restaurantId, UUID accountId) {
        scoped(restaurantId, accountId);
        auth.revoke(accountId);
        audit.record("STAFF_SESSIONS_REVOKED", restaurantId, accountId, "All sessions revoked");
    }

    @Transactional
    public com.nihal.restaurantordering.dto.auth.PasswordResetRequestResponse reset(UUID restaurantId, UUID accountId) {
        var account = scoped(restaurantId, accountId);
        var response = auth.requestPasswordReset(account.getUsername());
        audit.record("STAFF_RESET_REQUESTED", restaurantId, accountId, "Password reset requested");
        return response;
    }

    private RestaurantAdmin scoped(UUID restaurantId, UUID accountId) {
        return accounts.findForUpdate(accountId).filter(a -> restaurantId.equals(a.getRestaurantId()))
                .orElseThrow(() -> new NotFoundException("Staff account not found in this restaurant"));
    }

    private void requireRestaurantRole(AdminRole role) {
        if (role == AdminRole.PLATFORM_ADMIN) throw new ForbiddenException("Platform privileges cannot be assigned through staff management");
    }

    private StaffView view(RestaurantAdmin account) {
        return new StaffView(account.getId(), account.getUsername(), account.getEmail(), account.getRole(), account.isActive());
    }
}
