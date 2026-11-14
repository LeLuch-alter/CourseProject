package kz.kvit.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Dashboard totals, calculated like summarize() on the web site.
 * The money totals cover only tenge debts; other currencies are listed separately
 * in foreignOwed / foreignLent (remaining amount per currency code) and never converted.
 *
 * @param totalOriginal  what I owe (OWED, tenge, not cancelled): original amounts
 * @param totalPaid      what I already paid on those debts
 * @param totalRemaining what I still owe in tenge
 * @param activeCount    my open debts (any currency), overdue ones included
 * @param overdueCount   my overdue debts (any currency)
 * @param dueSoonCount   my active debts due within the next 14 days
 * @param lentTotal      what others owe me (LENT, tenge, not cancelled): original amounts
 * @param lentRemaining  what others still have to return in tenge
 * @param lentReturned   what others already returned in tenge
 */
public record DashboardSummary(
        BigDecimal totalOriginal,
        BigDecimal totalPaid,
        BigDecimal totalRemaining,
        int activeCount,
        int overdueCount,
        int dueSoonCount,
        BigDecimal lentTotal,
        BigDecimal lentRemaining,
        BigDecimal lentReturned,
        Map<String, BigDecimal> foreignOwed,
        Map<String, BigDecimal> foreignLent) {

    /** Nothing to show: no live debts in either direction or currency. */
    public boolean isEmpty() {
        return totalOriginal.signum() == 0 && lentTotal.signum() == 0
                && foreignOwed.isEmpty() && foreignLent.isEmpty();
    }
}
