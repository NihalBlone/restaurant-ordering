package com.nihal.restaurantordering.util;

import com.nihal.restaurantordering.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    public String sanitizeCustomerName(String input) {
        if (input == null) {
            throw new BadRequestException("customerName is required");
        }

        String sanitized = input
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (sanitized.isBlank()) {
            throw new BadRequestException("customerName must not be blank");
        }
        return sanitized;
    }
}
