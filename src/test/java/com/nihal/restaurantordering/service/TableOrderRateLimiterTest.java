package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableOrderRateLimiterTest {

    private final TableOrderRateLimiter rateLimiter = new TableOrderRateLimiter();

    @Test
    void rejectsRapidRepeatedOrdersForSameTable() {
        UUID tableId = UUID.randomUUID();

        rateLimiter.acquire(tableId);

        assertThatThrownBy(() -> rateLimiter.acquire(tableId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("5 seconds");
    }

    @Test
    void releaseClearsReservationForFailedOrderAttempt() {
        UUID tableId = UUID.randomUUID();

        Instant token = rateLimiter.acquire(tableId);
        rateLimiter.release(tableId, token);

        assertThat(rateLimiter.acquire(tableId)).isNotNull();
    }
}
