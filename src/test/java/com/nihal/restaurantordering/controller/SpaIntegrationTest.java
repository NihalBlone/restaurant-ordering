package com.nihal.restaurantordering.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SpaIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.security.oauth2.server.resource.web.BearerTokenResolver tokens;

    @Test void websocketHandshakeStillReadsTheStaffCookie() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/ws-orders/123/session/websocket");
        request.setCookies(new Cookie("restaurant_access_token", "staff-token"));
        org.assertj.core.api.Assertions.assertThat(tokens.resolve(request)).isEqualTo("staff-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/menu", "/cart", "/track", "/admin/login", "/admin/forgot-password",
            "/admin/reset-password", "/dashboard", "/platform", "/platform/login"})
    void pagesWorkWithExpiredCookie(String path) throws Exception {
        mvc.perform(get(path).cookie(new Cookie("restaurant_access_token", "expired")))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void publicAssetsDoNotRequireLogin() throws Exception {
        mvc.perform(get("/assets/test.js").cookie(new Cookie("restaurant_access_token", "expired")))
                .andExpect(status().isOk());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test void apisAreNotReplacedByHtml() throws Exception {
        mvc.perform(get("/api/platform/overview")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/menu")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
