package com.nihal.restaurantordering.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpaController {

    // Explicit UI routes only: API errors and missing assets must never become HTML.
    @GetMapping({"/", "/menu", "/cart", "/track", "/admin/login", "/admin/forgot-password",
            "/admin/reset-password", "/dashboard", "/platform", "/platform/login"})
    public ResponseEntity<Resource> application() {
        Resource index = new ClassPathResource("static/index.html");
        if (!index.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML).body(index);
    }
}
