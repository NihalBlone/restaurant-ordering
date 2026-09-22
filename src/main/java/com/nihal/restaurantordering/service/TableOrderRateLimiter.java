package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TableOrderRateLimiter {

    private static final Duration ORDER_WINDOW = Duration.ofSeconds(5);
    private static final Duration STALE_ENTRY_TTL = Duration.ofMinutes(30);

    private final ConcurrentMap<String, Instant> lastOrderTime = new ConcurrentHashMap<>();

    public Instant acquire(UUID tableId) {
        return acquire(tableId, null);
    }

    public Instant acquire(UUID tableId, String orderingClientId) {
        String limiterKey = buildLimiterKey(tableId, orderingClientId);
        Instant now = Instant.now();
        Instant[] retryAfter = new Instant[1];
        lastOrderTime.compute(limiterKey, (ignored, previous) -> {
            if (previous != null && Duration.between(previous, now).compareTo(ORDER_WINDOW) < 0) {
                retryAfter[0] = previous.plus(ORDER_WINDOW);
                return previous;
            }
            return now;
        });

        if (retryAfter[0] != null) {
            throw new ConflictException("Please wait 5 seconds before placing another order from this device. Retry after "
                    + retryAfter[0]);
        }
        cleanupStaleEntries(now);
        return now;
    }

    public void release(UUID tableId, Instant acquiredAt) {
        release(tableId, null, acquiredAt);
    }

    public void release(UUID tableId, String orderingClientId, Instant acquiredAt) {
        String limiterKey = buildLimiterKey(tableId, orderingClientId);
        lastOrderTime.computeIfPresent(
                limiterKey,
                (ignored, current) -> current.equals(acquiredAt) ? null : current
        );
    }

    private void cleanupStaleEntries(Instant now) {
        lastOrderTime.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(STALE_ENTRY_TTL) > 0);
    }

    private String buildLimiterKey(UUID tableId, String orderingClientId) {
        String clientId = orderingClientId == null || orderingClientId.isBlank()
                ? "anonymous"
                : orderingClientId.trim();
        return tableId + ":" + clientId;
    }
}
