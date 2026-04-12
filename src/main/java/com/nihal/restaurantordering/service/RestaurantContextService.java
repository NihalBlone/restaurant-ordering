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
        return restaurantTableRepository.findByIdAndActiveTrue(tableId)
                .orElseThrow(() -> new NotFoundException("Active table not found for id " + tableId));
    }

    public Restaurant getRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found for id " + restaurantId));
    }
}
