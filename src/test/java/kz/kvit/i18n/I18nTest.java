package kz.kvit.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nTest {

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(I18n.Language.RU);
    }

    @Test
    void everyLanguageHasExactlyTheSameKeys() throws IOException {
        Properties english = load("messages.properties");
        for (String file : new String[]{"messages_ru.properties", "messages_kk.properties"}) {
            assertEquals(english.stringPropertyNames(), load(file).stringPropertyNames(), file);
        }
    }

    @Test
    void everyMessageIsAValidPattern() throws IOException {
        for (String file : new String[]{"messages.properties", "messages_ru.properties", "messages_kk.properties"}) {
            Properties messages = load(file);
            for (String key : messages.stringPropertyNames()) {
                String text = messages.getProperty(key);
                assertDoesNotThrow(() -> new MessageFormat(text).format(new Object[]{1, 2}), file + ": " + key);
            }
        }
    }

    @Test
    void switchesLanguageAndFormatsArguments() {
        I18n.setLanguage(I18n.Language.KK);
        assertEquals("Қарыздар", I18n.t("nav.debts"));
        I18n.setLanguage(I18n.Language.EN);
        assertEquals("In 1 day", I18n.t("due.inDays", 1));
        assertEquals("In 5 days", I18n.t("due.inDays", 5));
        I18n.setLanguage(I18n.Language.RU);
        assertEquals("3 из 7", I18n.t("debts.count", 3, 7));
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = I18n.class.getResourceAsStream(file)) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
