package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.config.PlatformProperties;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlatformTotpServiceTest {
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @Test void matchesSixDigitSuffixesOfRfc6238Sha1Vectors() {
        assertThat(PlatformTotpService.code(SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(PlatformTotpService.code(SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(PlatformTotpService.code(SECRET, 20000000000L / 30)).isEqualTo("353130");
    }
    @Test void rejectsReplayedCodeAndMalformedSecrets() {
        var properties = new PlatformProperties(); properties.setTotpSecret(SECRET);
        var service = new PlatformTotpService(properties); var account = new RestaurantAdmin();
        String code = PlatformTotpService.code(SECRET, java.time.Instant.now().getEpochSecond() / 30);
        service.verify(account, code);
        assertThatThrownBy(() -> service.verify(account, code)).hasMessageContaining("already used");
        assertThatThrownBy(() -> PlatformTotpService.code("invalid!", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
