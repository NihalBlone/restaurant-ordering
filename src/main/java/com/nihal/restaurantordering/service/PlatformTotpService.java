package com.nihal.restaurantordering.service;

import com.nihal.restaurantordering.config.PlatformProperties;
import com.nihal.restaurantordering.domain.RestaurantAdmin;
import com.nihal.restaurantordering.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlatformTotpService {
    private final PlatformProperties properties;

    public void verify(RestaurantAdmin account, String supplied) {
        if (properties.getTotpSecret().isBlank()) return; // Production startup requires a secret.
        if (supplied == null || !supplied.matches("[0-9]{6}")) throw invalid();
        long step = Instant.now().getEpochSecond() / 30;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (candidate > account.getLastTotpStep() && MessageDigest.isEqual(
                    code(properties.getTotpSecret(), candidate).getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) {
                account.setLastTotpStep(candidate);
                return;
            }
        }
        throw invalid();
    }

    public static String code(String secret, long step) {
        try {
            var bytes = new ByteArrayOutputStream();
            int buffer = 0;
            int bits = 0;
            for (char c : secret.replace("=", "").toUpperCase(Locale.ROOT).toCharArray()) {
                int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
                if (value < 0) throw new IllegalArgumentException("TOTP secret must be Base32");
                buffer = (buffer << 5) | value;
                bits += 5;
                if (bits >= 8) { bits -= 8; bytes.write((buffer >> bits) & 255); }
            }
            if (bytes.size() < 20) throw new IllegalArgumentException("TOTP secret must contain at least 160 bits");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(bytes.toByteArray(), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP is unavailable", exception);
        }
    }

    private UnauthorizedException invalid() {
        return new UnauthorizedException("Invalid or already used authenticator code");
    }
}
