package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthAttemptLimiter {
    private final Map<String, Window> attempts = new HashMap<>();

    public synchronized void check(String key, int limit) {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> entry.getValue().until().isBefore(now));
        Window window = attempts.getOrDefault(key, new Window(now.plusSeconds(600), 0));
        if (window.count() >= limit || (attempts.size() >= 10000 && !attempts.containsKey(key))) {
            throw new TooManyRequestsException("Too many authentication attempts. Try again in 10 minutes.");
        }
        attempts.put(key, new Window(window.until(), window.count() + 1));
    }

    private record Window(Instant until, int count) {}
}
