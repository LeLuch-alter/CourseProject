package kz.kvit.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    // Validation does not touch the network, so no Supabase client is needed.
    private final AuthService authService = new AuthService(null);

    @Test
    void acceptsNormalEmail() {
        assertDoesNotThrow(() -> authService.validateEmail(" student@mail.kz "));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(ValidationException.class, () -> authService.validateEmail("student"));
        assertThrows(ValidationException.class, () -> authService.validateEmail("a@b"));
        assertThrows(ValidationException.class, () -> authService.validateEmail(null));
    }

    @Test
    void newPasswordNeedsLengthLetterAndDigit() {
        assertDoesNotThrow(() -> authService.validateNewPassword("qwerty12"));
        assertThrows(ValidationException.class, () -> authService.validateNewPassword("abc1"));
        assertThrows(ValidationException.class, () -> authService.validateNewPassword("onlyletters"));
        assertThrows(ValidationException.class, () -> authService.validateNewPassword("12345678"));
    }
}
