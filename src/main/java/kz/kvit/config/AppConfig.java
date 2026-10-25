package kz.kvit.config;

import kz.kvit.i18n.I18n;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/**
 * Reads the Supabase URL and publishable key from config.properties
 * (in the working directory) or from the environment variables
 * SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY.
 */
public class AppConfig {
    private static final Path CONFIG_FILE = Path.of("config.properties");

    private final String supabaseUrl;
    private final String publishableKey;

    private AppConfig(String supabaseUrl, String publishableKey) {
        this.supabaseUrl = supabaseUrl;
        this.publishableKey = publishableKey;
    }

    public static AppConfig load() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException(I18n.t("config.readFailed", e.getMessage()), e);
            }
        }

        String url = valueOf(properties, "supabase.url", "SUPABASE_URL");
        String key = valueOf(properties, "supabase.publishableKey", "SUPABASE_PUBLISHABLE_KEY");

        if (url == null || key == null) {
            throw new IllegalStateException(I18n.t("config.missing"));
        }
        if (!url.startsWith("https://")) {
            throw new IllegalStateException(I18n.t("config.https"));
        }
        if (isSecretKey(key)) {
            throw new IllegalStateException(I18n.t("config.secretKey"));
        }
        return new AppConfig(stripTrailingSlash(url), key);
    }

    /**
     * Security guard: refuses keys that bypass Row Level Security.
     * New-style secret keys start with "sb_secret_"; legacy keys are JWTs whose payload has role "service_role".
     */
    static boolean isSecretKey(String key) {
        if (key.startsWith("sb_secret_")) {
            return true;
        }
        String[] jwtParts = key.split("\\.");
        if (jwtParts.length == 3) {
            try {
                String payload = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
                return payload.replace(" ", "").contains("\"role\":\"service_role\"");
            } catch (IllegalArgumentException e) {
                // Not a valid JWT: then it is not a legacy service_role key either.
                return false;
            }
        }
        return false;
    }

    private static String valueOf(Properties properties, String propertyName, String envName) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getPublishableKey() {
        return publishableKey;
    }
}
