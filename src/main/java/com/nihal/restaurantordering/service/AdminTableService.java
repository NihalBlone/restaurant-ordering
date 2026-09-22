package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.domain.RestaurantTable;
import com.nihal.restaurantordering.dto.admin.AdminTableResponse;
import com.nihal.restaurantordering.dto.admin.CreateTableRequest;
import com.nihal.restaurantordering.exception.ConflictException;
import com.nihal.restaurantordering.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTableService {

    private final RestaurantTableRepository restaurantTableRepository;
    private final TableLinkService tableLinkService;
    private final com.nihal.restaurantordering.repository.RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<AdminTableResponse> getTables(UUID restaurantId) {
        return restaurantTableRepository.findAllByRestaurantIdOrderByTableNumberAsc(restaurantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminTableResponse createTable(UUID restaurantId, CreateTableRequest request) {
        var restaurant = restaurantRepository.findForUpdate(restaurantId)
                .orElseThrow(() -> new com.nihal.restaurantordering.exception.NotFoundException("Restaurant not found"));
        if (restaurantTableRepository.countByRestaurantId(restaurantId) >= restaurant.getTableLimit()) {
            throw new ConflictException("Table limit reached. Contact the platform owner to increase your limit.");
        }
        if (restaurantTableRepository.existsByRestaurantIdAndTableNumberIgnoreCase(restaurantId, request.tableNumber())) {
            throw duplicateTable();
        }

        RestaurantTable table = new RestaurantTable();
        table.setRestaurantId(restaurantId);
        table.setTableNumber(request.tableNumber());
        table.setActive(true);
        // Hibernate assigns the UUID on persist; build the final URL from that ID.
        table.setQrCodeUrl(tableLinkService.menuUrl());
        try {
            table = restaurantTableRepository.saveAndFlush(table);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                                .contains("uk_restaurant_table_number")) {
                    throw duplicateTable();
                }
            }
            throw exception;
        }
        table.setQrCodeUrl(tableLinkService.forTable(table.getId()));
        restaurantTableRepository.flush();
        log.info("Table created tableId={} restaurantId={}", table.getId(), restaurantId);
        return toResponse(table);
    }

    private ConflictException duplicateTable() {
        return new ConflictException("A table with this name already exists in your restaurant. Choose another name.");
    }

    private AdminTableResponse toResponse(RestaurantTable table) {
        return AdminTableResponse.builder()
                .id(table.getId())
                .tableNumber(table.getTableNumber())
                // Also refresh links for tables seeded before the public URL was configured.
                .qrCodeUrl(tableLinkService.forTable(table.getId()))
                .active(table.isActive())
                .currentSessionId(table.getCurrentSessionId())
                .build();
    }
}
