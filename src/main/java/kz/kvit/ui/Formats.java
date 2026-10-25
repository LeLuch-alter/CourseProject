package kz.kvit.ui;

import kz.kvit.i18n.I18n;
import kz.kvit.model.DateRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

/** Formatting of money, dates and numbers in the current language. */
public final class Formats {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private Formats() {
    }

    /** 350000.5 -> "350 000,50 ₸" (decimal point in English). */
    public static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        boolean english = I18n.getLanguage() == I18n.Language.EN;
        symbols.setGroupingSeparator(english ? ',' : ' ');
        symbols.setDecimalSeparator(english ? '.' : ',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(amount) + " ₸";
    }

    /** Short form for chart axes: 350 000 -> "350 тыс." / "350K". */
    public static String compact(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCompactNumberInstance(I18n.locale(), NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        return format.format(amount);
    }

    public static String date(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    /** "25 октября 2026" / "25 October 2026" */
    public static String longDate(LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", I18n.locale()).format(date);
    }

    /** "25 октября 2026" for one day, "1 октября — 31 октября 2026" or "28 декабря 2026 — 3 января 2027" for a period. */
    public static String dateRange(DateRange range) {
        if (range.isSingleDay()) {
            return longDate(range.start());
        }
        if (range.start().getYear() == range.end().getYear()) {
            return dayMonth(range.start()) + " — " + longDate(range.end());
        }
        return longDate(range.start()) + " — " + longDate(range.end());
    }

    /** "15 ноября" / "15 November" */
    public static String dayMonth(LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMMM", I18n.locale()).format(date);
    }

    /** "окт." / "Oct" — for chart labels. */
    public static String monthShort(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale());
    }

    /** "октябрь 2026" / "October 2026" */
    public static String monthYear(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, I18n.locale()) + " " + month.getYear();
    }

    /** Whole percent of part in total, rounded down: 0.389 -> 38. Returns 0 when total is zero. */
    public static int percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.DOWN).intValue();
    }
}
