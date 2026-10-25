package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.repository.SupabaseClient;

import java.util.regex.Pattern;

public class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MIN_PASSWORD_LENGTH = 8;

    private final SupabaseClient client;

    public AuthService(SupabaseClient client) {
        this.client = client;
    }

    public void signIn(String email, String password) {
        validateEmail(email);
        if (password == null || password.isEmpty()) {
            throw new ValidationException(I18n.t("error.auth.password"));
        }
        client.signIn(email.trim(), password);
    }

    /** @return true if logged in immediately, false if the user must confirm the email first */
    public boolean signUp(String email, String password) {
        validateEmail(email);
        validateNewPassword(password);
        return client.signUp(email.trim(), password);
    }

    public void signOut() {
        client.signOut();
    }

    public String getCurrentUserEmail() {
        return client.getCurrentUserEmail();
    }

    void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException(I18n.t("error.auth.email"));
        }
    }

    void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(I18n.t("error.auth.passwordLength", MIN_PASSWORD_LENGTH));
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException(I18n.t("error.auth.passwordChars"));
        }
    }
}
