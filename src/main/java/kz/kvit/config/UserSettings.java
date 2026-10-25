package kz.kvit.config;

import kz.kvit.i18n.I18n;

import java.util.prefs.Preferences;

/**
 * Personal settings (theme and language) that survive a restart.
 * Stored with java.util.prefs, which on Windows keeps them in the user's registry branch.
 */
public class UserSettings {

    public enum ThemeChoice { SYSTEM, DARK, LIGHT }

    private static final String THEME_KEY = "theme";
    private static final String LANGUAGE_KEY = "language";
    private static final String EMAIL_KEY = "lastEmail";

    private final Preferences preferences = Preferences.userRoot().node("kz/kvit");

    public ThemeChoice getTheme() {
        return parse(ThemeChoice.class, preferences.get(THEME_KEY, ThemeChoice.SYSTEM.name()), ThemeChoice.SYSTEM);
    }

    public void setTheme(ThemeChoice theme) {
        preferences.put(THEME_KEY, theme.name());
    }

    public I18n.Language getLanguage() {
        return parse(I18n.Language.class, preferences.get(LANGUAGE_KEY, I18n.Language.RU.name()), I18n.Language.RU);
    }

    public void setLanguage(I18n.Language language) {
        preferences.put(LANGUAGE_KEY, language.name());
    }

    /** Email of the last successful sign-in, so it does not have to be typed again. Never the password. */
    public String getLastEmail() {
        return preferences.get(EMAIL_KEY, "");
    }

    public void setLastEmail(String email) {
        preferences.put(EMAIL_KEY, email == null ? "" : email.trim());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            // An unknown value (e.g. from an older version) falls back to the default.
            return fallback;
        }
    }
}
