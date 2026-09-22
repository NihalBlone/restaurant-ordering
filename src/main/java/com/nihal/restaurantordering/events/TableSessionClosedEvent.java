package com.nihal.restaurantordering.events;

import com.nihal.restaurantordering.dto.session.TableSessionCloseResponse;

public record TableSessionClosedEvent(TableSessionCloseResponse session) {
}
