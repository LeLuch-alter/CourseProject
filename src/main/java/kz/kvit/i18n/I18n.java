package kz.kvit.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Translations. Texts live in messages*.properties next to this class:
 * messages.properties (English), messages_ru.properties, messages_kk.properties.
 */
public final class I18n {

    public enum Language {
        RU("ru", "Русский", "RU"),
        KK("kk", "Қазақша", "KZ"),
        EN("en", "English", "EN");

        private final Locale locale;
        private final String nativeName;
        private final String shortName;

        Language(String tag, String nativeName, String shortName) {
            this.locale = Locale.forLanguageTag(tag);
            this.nativeName = nativeName;
            this.shortName = shortName;
        }

        public Locale getLocale() {
            return locale;
        }

        public String getNativeName() {
            return nativeName;
        }

        public String getShortName() {
            return shortName;
        }
    }

    private static final String BUNDLE = "kz.kvit.i18n.messages";

    private static Language language = Language.RU;
    private static ResourceBundle bundle = load(Language.RU);

    private I18n() {
    }

    public static void setLanguage(Language newLanguage) {
        language = newLanguage;
        bundle = load(newLanguage);
    }

    public static Language getLanguage() {
        return language;
    }

    public static Locale locale() {
        return language.getLocale();
    }

    /** Text for the key; {0}, {1}... are replaced by the arguments. */
    public static String t(String key, Object... args) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            System.err.println("Missing translation: " + key + " (" + language + ")");
            return key;
        }
        return args.length == 0 ? text : new MessageFormat(text, locale()).format(args);
    }

    private static ResourceBundle load(Language lang) {
        // No fallback to the computer's language: English (the base file) is the only fallback.
        return ResourceBundle.getBundle(BUNDLE, lang.getLocale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
