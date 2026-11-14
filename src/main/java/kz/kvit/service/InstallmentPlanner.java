package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Installment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a payment schedule: the remaining balance split into equal monthly instalments.
 * The same rule as splitEqually() on the web site: every part is rounded down to the tiyn and the
 * last part takes the rounding remainder, so the parts always add up exactly to the balance.
 */
public class InstallmentPlanner {
    public static final int MAX_PARTS = 60;

    public List<BigDecimal> splitEqually(BigDecimal total, int parts) {
        BigDecimal base = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < parts - 1; i++) {
            amounts.add(base);
        }
        amounts.add(total.subtract(base.multiply(BigDecimal.valueOf(parts - 1))).setScale(2, RoundingMode.UNNECESSARY));
        return amounts;
    }

    /**
     * One instalment per month starting at firstDate. plusMonths keeps the day of month and moves to
     * the last day of a shorter month (31 January -> 28 February), so no month is skipped.
     */
    public List<Installment> buildSchedule(long debtId, BigDecimal remaining, int parts, LocalDate firstDate,
                                           LocalDate today) {
        validate(remaining, parts, firstDate, today);
        List<BigDecimal> amounts = splitEqually(remaining, parts);
        List<Installment> schedule = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            schedule.add(new Installment(null, debtId, i + 1, firstDate.plusMonths(i), amounts.get(i), null));
        }
        return schedule;
    }

    /** Number of parts typed by the user, e.g. "6"; throws a readable error for anything else. */
    public int parseParts(String text) {
        String cleaned = text == null ? "" : text.trim();
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            throw new ValidationException(I18n.t("schedule.error.parts", MAX_PARTS));
        }
    }

    void validate(BigDecimal remaining, int parts, LocalDate firstDate, LocalDate today) {
        if (remaining == null || remaining.signum() <= 0) {
            throw new ValidationException(I18n.t("schedule.error.nothingLeft"));
        }
        if (parts < 1 || parts > MAX_PARTS) {
            throw new ValidationException(I18n.t("schedule.error.parts", MAX_PARTS));
        }
        // Less than one tiyn per part cannot be stored (amount must be > 0).
        if (remaining.compareTo(new BigDecimal("0.01").multiply(BigDecimal.valueOf(parts))) < 0) {
            throw new ValidationException(I18n.t("schedule.error.tooSmall"));
        }
        if (firstDate == null) {
            throw new ValidationException(I18n.t("schedule.error.noDate"));
        }
        if (firstDate.isBefore(today)) {
            throw new ValidationException(I18n.t("schedule.error.pastDate"));
        }
    }
}
