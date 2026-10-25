package kz.kvit.service;

import kz.kvit.model.DashboardSummary;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Balance calculations. Interest, fees and penalties are NOT applied:
 * remaining balance = original amount - sum of recorded payments.
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

    public BigDecimal remainingBalance(Debt debt, List<Payment> paymentsOfDebt) {
        return debt.getOriginalAmount().subtract(totalPaid(paymentsOfDebt));
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

    /** Dashboard totals. Cancelled debts are excluded so they do not inflate the totals. */
    public DashboardSummary summarize(List<Debt> debts, List<Payment> allPayments, LocalDate today) {
        BigDecimal totalOriginal = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int activeCount = 0;
        int overdueCount = 0;

        for (Debt debt : debts) {
            if (debt.getStatus() == DebtStatus.CANCELLED) {
                continue;
            }
            List<Payment> paymentsOfDebt = paymentsOf(debt, allPayments);
            totalOriginal = totalOriginal.add(debt.getOriginalAmount());
            totalPaid = totalPaid.add(totalPaid(paymentsOfDebt));
            if (debt.getStatus() == DebtStatus.ACTIVE) {
                activeCount++;
            }
            if (isOverdue(debt, paymentsOfDebt, today)) {
                overdueCount++;
            }
        }

        return new DashboardSummary(totalOriginal, totalPaid, totalOriginal.subtract(totalPaid),
                activeCount, overdueCount);
    }

    public DebtOverview overview(Debt debt, List<Payment> paymentsOfDebt, LocalDate today) {
        BigDecimal paid = totalPaid(paymentsOfDebt);
        return new DebtOverview(debt, paid, debt.getOriginalAmount().subtract(paid),
                isOverdue(debt, paymentsOfDebt, today));
    }

    /**
     * Debts that need attention: active, not fully paid, and either overdue or due within
     * the next ATTENTION_DAYS days. Sorted by due date, so overdue ones come first.
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
}
