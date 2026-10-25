package kz.kvit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * "What if" calculations for paying off one debt with equal monthly payments.
 * Interest is not included, the same as everywhere else in the app.
 */
public class PayoffPlanner {

    public record Plan(int months, LocalDate payoffDate) {
    }

    /** Paying {@code monthly} every month: how many months until the remaining amount is covered. */
    public Plan planByMonthlyPayment(BigDecimal remaining, BigDecimal monthly, LocalDate today) {
        if (monthly == null || monthly.signum() <= 0) {
            throw new IllegalArgumentException("Monthly payment must be positive");
        }
        if (remaining.signum() <= 0) {
            return new Plan(0, today);
        }
        int months = remaining.divide(monthly, 0, RoundingMode.CEILING).intValue();
        return new Plan(months, today.plusMonths(months));
    }

    /**
     * Monthly payment needed to finish by the target date. Counts calendar months from this month
     * to the target month (at least one), and rounds the payment up to whole tenge so the sum
     * of payments is never short.
     */
    public BigDecimal monthlyPaymentToFinishBy(BigDecimal remaining, LocalDate target, LocalDate today) {
        if (remaining.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)));
        return remaining.divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING);
    }
}
