package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.Restaurant;
import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.exception.NotFoundException;
import com.nihal.restaurantordering.repository.RestaurantRepository;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantContextService {

    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantRepository restaurantRepository;

    public RestaurantTable getActiveTable(UUID tableId) {
        var table = restaurantTableRepository.findByIdAndActiveTrue(tableId)
                .orElseThrow(() -> new NotFoundException("Active table not found for id " + tableId));
        requireActive(table.getRestaurantId());
        return table;
    }

    public RestaurantTable getActiveTableForUpdate(UUID tableId) {
        var table = restaurantTableRepository.findActiveByIdForUpdate(tableId)
                .orElseThrow(() -> new NotFoundException("Active table not found for id " + tableId));
        requireActive(table.getRestaurantId());
        return table;
    }

    public Restaurant getRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found for id " + restaurantId));
    }

    private void requireActive(UUID restaurantId) {
        if (getRestaurant(restaurantId).getStatus() != com.nihal.restaurantordering.domain.RestaurantStatus.ACTIVE) {
            throw new com.nihal.restaurantordering.exception.ForbiddenException("This restaurant is not accepting orders right now");
        }
    }
}
