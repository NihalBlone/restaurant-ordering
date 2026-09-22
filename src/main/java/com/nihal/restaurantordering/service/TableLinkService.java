package com.nihal.restaurantordering.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
public class TableLinkService {

    private final String menuUrl;

    public TableLinkService(@Value("${app.customer-base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl.strip());
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("APP_CUSTOMER_BASE_URL must be an HTTP(S) URL without credentials, query or fragment");
        }
        menuUrl = uri.toASCIIString().replaceAll("/+$", "") + "/menu";
        if (menuUrl.length() > 450) {
            throw new IllegalArgumentException("APP_CUSTOMER_BASE_URL is too long for table QR links");
        }
    }

    public String menuUrl() {
        return menuUrl;
    }

    public String forTable(UUID tableId) {
        return menuUrl + "?tableId=" + tableId;
    }
}
