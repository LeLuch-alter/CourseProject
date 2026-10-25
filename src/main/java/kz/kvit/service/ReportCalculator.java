package kz.kvit.service;

import kz.kvit.model.CreditorTotal;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.MonthTotal;
import kz.kvit.model.Payment;
import kz.kvit.model.PayoffForecast;
import kz.kvit.model.StatusBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculations for the reports screen. No database or UI, so they are easy to test. */
public class ReportCalculator {
    static final int FORECAST_MONTHS = 3;
    static final int MAX_CREDITORS = 6;

    private final DebtCalculator calculator = new DebtCalculator();

    /** Payment totals for each of the last {@code months} months (current month included, empty months = 0). */
    public List<MonthTotal> paymentsByMonth(List<Payment> payments, YearMonth currentMonth, int months) {
        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            totals.put(currentMonth.minusMonths(i), BigDecimal.ZERO);
        }
        for (Payment payment : payments) {
            YearMonth month = YearMonth.from(payment.getPaymentDate());
            if (totals.containsKey(month)) {
                totals.put(month, totals.get(month).add(payment.getAmount()));
            }
        }
        List<MonthTotal> result = new ArrayList<>();
        totals.forEach((month, total) -> result.add(new MonthTotal(month, total)));
        return result;
    }

    /**
     * Original and remaining amount per creditor, largest remaining first. Cancelled debts and
     * creditors that are fully paid off are left out.
     * Creditors beyond the first MAX_CREDITORS are folded into one "other" row with a null name.
     */
    public List<CreditorTotal> byCreditor(List<Debt> debts, List<Payment> payments) {
        Map<String, CreditorTotal> totals = new LinkedHashMap<>();
        for (Debt debt : debts) {
            if (debt.getStatus() == DebtStatus.CANCELLED) {
                continue;
            }
            BigDecimal remaining = calculator.remainingBalance(debt, calculator.paymentsOf(debt, payments));
            // Creditor names typed slightly differently ("Kaspi" / "kaspi ") count as one.
            String key = debt.getCreditorName().trim().toLowerCase();
            CreditorTotal old = totals.get(key);
            totals.put(key, old == null
                    ? new CreditorTotal(debt.getCreditorName().trim(), debt.getOriginalAmount(), remaining, 1)
                    : new CreditorTotal(old.creditorName(), old.original().add(debt.getOriginalAmount()),
                    old.remaining().add(remaining), old.debtCount() + 1));
        }

        List<CreditorTotal> sorted = new ArrayList<>(totals.values());
        sorted.removeIf(total -> total.remaining().signum() <= 0);
        sorted.sort(Comparator.comparing(CreditorTotal::remaining).reversed());
        if (sorted.size() <= MAX_CREDITORS) {
            return sorted;
        }

        List<CreditorTotal> result = new ArrayList<>(sorted.subList(0, MAX_CREDITORS - 1));
        BigDecimal original = BigDecimal.ZERO;
        BigDecimal remaining = BigDecimal.ZERO;
        int count = 0;
        for (CreditorTotal rest : sorted.subList(MAX_CREDITORS - 1, sorted.size())) {
            original = original.add(rest.original());
            remaining = remaining.add(rest.remaining());
            count += rest.debtCount();
        }
        result.add(new CreditorTotal(null, original, remaining, count));
        return result;
    }

    public StatusBreakdown statuses(List<Debt> debts, List<Payment> payments, LocalDate today) {
        int active = 0;
        int overdue = 0;
        int paid = 0;
        int cancelled = 0;
        for (Debt debt : debts) {
            switch (debt.getStatus()) {
                case PAID -> paid++;
                case CANCELLED -> cancelled++;
                case ACTIVE -> {
                    if (calculator.isOverdue(debt, calculator.paymentsOf(debt, payments), today)) {
                        overdue++;
                    } else {
                        active++;
                    }
                }
            }
        }
        return new StatusBreakdown(active, overdue, paid, cancelled);
    }

    /**
     * Estimate only: average monthly payment over the last FORECAST_MONTHS months,
     * months left = remaining / average (rounded up). No interest is taken into account.
     * Returns null if nothing is left or nothing was paid in that period.
     */
    public PayoffForecast forecast(BigDecimal remaining, List<Payment> payments, LocalDate today) {
        if (remaining.signum() <= 0) {
            return null;
        }
        LocalDate from = today.minusMonths(FORECAST_MONTHS);
        BigDecimal recent = BigDecimal.ZERO;
        for (Payment payment : payments) {
            LocalDate date = payment.getPaymentDate();
            if (date.isAfter(from) && !date.isAfter(today)) {
                recent = recent.add(payment.getAmount());
            }
        }
        if (recent.signum() == 0) {
            return null;
        }
        BigDecimal average = recent.divide(BigDecimal.valueOf(FORECAST_MONTHS), 2, RoundingMode.HALF_UP);
        int monthsLeft = remaining.divide(average, 0, RoundingMode.CEILING).intValue();
        return new PayoffForecast(average, monthsLeft, today.plusMonths(monthsLeft));
    }
}
