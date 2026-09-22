package com.nihal.restaurantordering.util;

import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    public String sanitizeCustomerName(String input) {
        if (input == null || input.isBlank()) {
            return "Guest";
        }

        String sanitized = input
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (sanitized.isBlank()) {
            return "Guest";
        }
        return sanitized;
    }
}
