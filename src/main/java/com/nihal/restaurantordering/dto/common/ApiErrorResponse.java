package com.nihal.restaurantordering.dto.common;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
}
