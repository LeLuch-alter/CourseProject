package kz.kvit.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void publishableKeysAreAllowed() {
        assertFalse(AppConfig.isSecretKey("sb_publishable_abc123"));
        assertFalse(AppConfig.isSecretKey(fakeJwt("{\"role\":\"anon\"}")));
    }

    @Test
    void secretKeysAreRejected() {
        assertTrue(AppConfig.isSecretKey("sb_secret_abc123"));
        assertTrue(AppConfig.isSecretKey(fakeJwt("{\"iss\":\"supabase\",\"role\":\"service_role\"}")));
    }

    private static String fakeJwt(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
