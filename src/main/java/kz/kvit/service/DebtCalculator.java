package kz.kvit.service;

import kz.kvit.model.DashboardSummary;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtDirection;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.EffectiveStatus;
import kz.kvit.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Balance calculations. Interest, fees and penalties are NOT applied:
 * remaining balance = original amount - sum of recorded payments.
 * The rules are the same as in web/src/lib/calc.ts, so the desktop app and the web site
 * always show the same numbers for the same data.
 */
public class DebtCalculator {
    static final int ATTENTION_DAYS = 14;

    public BigDecimal totalPaid(List<Payment> payments) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payment payment : payments) {
            total = total.add(payment.getAmount());
        }
        return total;
    }

    /** Never below zero, even if the payments add up to more than the debt. */
    public BigDecimal remainingBalance(Debt debt, List<Payment> paymentsOfDebt) {
        return debt.getOriginalAmount().subtract(totalPaid(paymentsOfDebt)).max(BigDecimal.ZERO);
    }

    /**
     * Overdue = due date is before today and something is still left to pay.
     * Cancelled debts are never overdue: the user decided they are no longer owed.
     */
    public boolean isOverdue(Debt debt, List<Payment> paymentsOfDebt, LocalDate today) {
        if (debt.getStatus() == DebtStatus.CANCELLED || debt.getDueDate() == null) {
            return false;
        }
        return debt.getDueDate().isBefore(today)
                && remainingBalance(debt, paymentsOfDebt).signum() > 0;
    }

    /** Active, something left to pay, and the due date is today or within the next ATTENTION_DAYS days. */
    public boolean isDueSoon(Debt debt, List<Payment> paymentsOfDebt, LocalDate today) {
        if (debt.getStatus() != DebtStatus.ACTIVE || debt.getDueDate() == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(today, debt.getDueDate());
        return days >= 0 && days <= ATTENTION_DAYS && remainingBalance(debt, paymentsOfDebt).signum() > 0;
    }

    public EffectiveStatus effectiveStatus(Debt debt, List<Payment> paymentsOfDebt, LocalDate today) {
        return overview(debt, paymentsOfDebt, today).effectiveStatus();
    }

    /**
     * Status after payments change: fully paid becomes PAID, and a PAID debt goes back
     * to ACTIVE if a payment was removed. CANCELLED is only changed by the user.
     */
    public DebtStatus statusAfterPayments(Debt debt, List<Payment> paymentsOfDebt) {
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            return DebtStatus.CANCELLED;
        }
        boolean fullyPaid = remainingBalance(debt, paymentsOfDebt).signum() <= 0;
        return fullyPaid ? DebtStatus.PAID : DebtStatus.ACTIVE;
    }

    /**
     * Dashboard totals, the same as summarize() on the web site:
     * - cancelled debts are left out completely;
     * - "I owe" (OWED) and "owed to me" (LENT) are counted separately;
     * - money totals are in tenge only; other currencies are listed per currency and never converted;
     * - the counters (active, overdue, due soon) count my OWED debts in every currency.
     */
    public DashboardSummary summarize(List<Debt> debts, List<Payment> allPayments, LocalDate today) {
        BigDecimal owedOriginal = BigDecimal.ZERO;
        BigDecimal owedPaid = BigDecimal.ZERO;
        BigDecimal lentOriginal = BigDecimal.ZERO;
        BigDecimal lentRemaining = BigDecimal.ZERO;
        Map<String, BigDecimal> foreignOwed = new TreeMap<>();
        Map<String, BigDecimal> foreignLent = new TreeMap<>();
        int activeCount = 0;
        int overdueCount = 0;
        int dueSoonCount = 0;

        for (Debt debt : debts) {
            if (debt.getStatus() == DebtStatus.CANCELLED) {
                continue;
            }
            List<Payment> paymentsOfDebt = paymentsOf(debt, allPayments);
            BigDecimal remaining = remainingBalance(debt, paymentsOfDebt);
            boolean owed = debt.getDirection() == DebtDirection.OWED;

            if (!debt.isInTenge()) {
                if (remaining.signum() > 0) {
                    (owed ? foreignOwed : foreignLent).merge(debt.getCurrency(), remaining, BigDecimal::add);
                }
            } else if (owed) {
                owedOriginal = owedOriginal.add(debt.getOriginalAmount());
                owedPaid = owedPaid.add(totalPaid(paymentsOfDebt));
            } else {
                lentOriginal = lentOriginal.add(debt.getOriginalAmount());
                lentRemaining = lentRemaining.add(remaining);
            }

            if (owed) {
                EffectiveStatus status = effectiveStatus(debt, paymentsOfDebt, today);
                if (status == EffectiveStatus.ACTIVE || status == EffectiveStatus.OVERDUE) {
                    activeCount++;
                }
                if (isOverdue(debt, paymentsOfDebt, today)) {
                    overdueCount++;
                }
                if (isDueSoon(debt, paymentsOfDebt, today)) {
                    dueSoonCount++;
                }
            }
        }

        return new DashboardSummary(owedOriginal, owedPaid, owedOriginal.subtract(owedPaid),
                activeCount, overdueCount, dueSoonCount,
                lentOriginal, lentRemaining, lentOriginal.subtract(lentRemaining),
                foreignOwed, foreignLent);
    }

    public DebtOverview overview(Debt debt, List<Payment> paymentsOfDebt, LocalDate today) {
        return new DebtOverview(debt, totalPaid(paymentsOfDebt), remainingBalance(debt, paymentsOfDebt),
                isOverdue(debt, paymentsOfDebt, today));
    }

    /**
     * Debts that need attention: active, not fully paid, and either overdue or due within
     * the next ATTENTION_DAYS days. Both directions are included (a friend's overdue return
     * needs attention too). Sorted by due date, so overdue ones come first.
     */
    public List<DebtOverview> needingAttention(List<Debt> debts, List<Payment> allPayments, LocalDate today) {
        LocalDate limit = today.plusDays(ATTENTION_DAYS);
        return debts.stream()
                .filter(debt -> debt.getStatus() == DebtStatus.ACTIVE)
                .filter(debt -> debt.getDueDate() != null && !debt.getDueDate().isAfter(limit))
                .map(debt -> overview(debt, paymentsOf(debt, allPayments), today))
                .filter(overview -> overview.remaining().signum() > 0)
                .sorted(Comparator.comparing(overview -> overview.debt().getDueDate()))
                .toList();
    }

    public List<Payment> paymentsOf(Debt debt, List<Payment> allPayments) {
        return allPayments.stream()
                .filter(payment -> payment.getDebtId().equals(debt.getId()))
                .toList();
    }

    /** My debts in tenge that are not cancelled: the set used by the reports and the forecast. */
    public List<Debt> owedInTenge(List<Debt> debts) {
        return debts.stream()
                .filter(debt -> debt.getDirection() == DebtDirection.OWED)
                .filter(debt -> debt.getStatus() != DebtStatus.CANCELLED)
                .filter(Debt::isInTenge)
                .toList();
    }

    /** Payments that belong to one of the given debts. */
    public List<Payment> paymentsOfDebts(List<Debt> debts, List<Payment> allPayments) {
        List<Long> ids = debts.stream().map(Debt::getId).toList();
        return allPayments.stream().filter(payment -> ids.contains(payment.getDebtId())).toList();
    }
}
