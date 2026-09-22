package com.nihal.restaurantordering.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableLinkServiceTest {
    @Test
    void buildsCanonicalLinkWithRealTableId() {
        UUID tableId = UUID.randomUUID();
        assertThat(new TableLinkService("https://order.example.com/").forTable(tableId))
                .isEqualTo("https://order.example.com/menu?tableId=" + tableId);
        assertThat(new TableLinkService("http://192.168.1.20:5173").forTable(tableId))
                .isEqualTo("http://192.168.1.20:5173/menu?tableId=" + tableId);
    }

    @Test
    void rejectsUnsafeOrAmbiguousConfiguration() {
        for (String url : List.of("javascript:alert(1)", "/menu", "https://user:pass@example.com",
                "https://example.com?tableId=bad", "https://example.com#menu")) {
            assertThatThrownBy(() -> new TableLinkService(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
