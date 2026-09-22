package com.nihal.restaurantordering.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputSanitizerTest {

    private final InputSanitizer inputSanitizer = new InputSanitizer();

    @Test
    void missingCustomerNameFallsBackToGuest() {
        assertThat(inputSanitizer.sanitizeCustomerName(null)).isEqualTo("Guest");
        assertThat(inputSanitizer.sanitizeCustomerName("   ")).isEqualTo("Guest");
    }
}
