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

    private final ConcurrentMap<UUID, Instant> lastOrderByTable = new ConcurrentHashMap<>();

    public Instant acquire(UUID tableId) {
        Instant now = Instant.now();
        Instant[] retryAfter = new Instant[1];
        lastOrderByTable.compute(tableId, (ignored, previous) -> {
            if (previous != null && Duration.between(previous, now).compareTo(ORDER_WINDOW) < 0) {
                retryAfter[0] = previous.plus(ORDER_WINDOW);
                return previous;
            }
            return now;
        });

        if (retryAfter[0] != null) {
            throw new ConflictException("Only one order per table is allowed every 5 seconds. Retry after " + retryAfter[0]);
        }
        return now;
    }

    public void release(UUID tableId, Instant acquiredAt) {
        lastOrderByTable.computeIfPresent(tableId, (ignored, current) -> current.equals(acquiredAt) ? null : current);
    }
}
