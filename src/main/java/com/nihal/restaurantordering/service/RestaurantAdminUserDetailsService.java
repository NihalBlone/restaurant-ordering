package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.repository.RestaurantAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RestaurantAdminUserDetailsService implements UserDetailsService {

    private final RestaurantAdminRepository restaurantAdminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        RestaurantAdmin admin = restaurantAdminRepository
                .findByUsernameNormalized(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        return User.withUsername(admin.getUsernameNormalized())
                .password(admin.getPasswordHash())
                .roles(admin.getRole().name())
                .disabled(!admin.isActive())
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
