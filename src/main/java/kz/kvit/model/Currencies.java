package kz.kvit.model;

import java.util.List;

/**
 * Currencies a debt can be recorded in (the same list as the database check constraint).
 * Kvit never converts between currencies: totals are kept separately for each one.
 */
public final class Currencies {
    public static final String TENGE = "KZT";
    public static final List<String> ALL = List.of("KZT", "USD", "EUR", "RUB");

    private Currencies() {
    }

    public static String symbol(String code) {
        if (code == null) {
            return "₸";
        }
        return switch (code) {
            case "KZT" -> "₸";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "RUB" -> "₽";
            default -> code;
        };
    }

    public static boolean isSupported(String code) {
        return ALL.contains(code);
    }
}
