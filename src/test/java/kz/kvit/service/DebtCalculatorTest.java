package kz.kvit.service;

import kz.kvit.model.DashboardSummary;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebtCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final DebtCalculator calculator = new DebtCalculator();

    @Test
    void remainingBalanceIsOriginalMinusPayments() {
        Debt debt = debt(1L, "100000.00", null, DebtStatus.ACTIVE);
        List<Payment> payments = List.of(payment(1L, "30000.00"), payment(1L, "20000.50"));

        assertEquals(new BigDecimal("50000.50"), calculator.totalPaid(payments));
        assertEquals(new BigDecimal("49999.50"), calculator.remainingBalance(debt, payments));
    }

    @Test
    void noPaymentsMeansNothingPaid() {
        Debt debt = debt(1L, "5000.00", null, DebtStatus.ACTIVE);

        assertEquals(BigDecimal.ZERO, calculator.totalPaid(List.of()));
        assertEquals(new BigDecimal("5000.00"), calculator.remainingBalance(debt, List.of()));
    }

    @Test
    void debtIsOverdueWhenDueDatePassedAndBalanceLeft() {
        Debt debt = debt(1L, "1000.00", TODAY.minusDays(1), DebtStatus.ACTIVE);

        assertTrue(calculator.isOverdue(debt, List.of(payment(1L, "999.99")), TODAY));
    }

    @Test
    void debtIsNotOverdueOnDueDateItself() {
        Debt debt = debt(1L, "1000.00", TODAY, DebtStatus.ACTIVE);

        assertFalse(calculator.isOverdue(debt, List.of(), TODAY));
    }

    @Test
    void fullyPaidOrCancelledOrNoDueDateIsNotOverdue() {
        Debt paid = debt(1L, "1000.00", TODAY.minusDays(5), DebtStatus.ACTIVE);
        Debt cancelled = debt(2L, "1000.00", TODAY.minusDays(5), DebtStatus.CANCELLED);
        Debt noDueDate = debt(3L, "1000.00", null, DebtStatus.ACTIVE);

        assertFalse(calculator.isOverdue(paid, List.of(payment(1L, "1000.00")), TODAY));
        assertFalse(calculator.isOverdue(cancelled, List.of(), TODAY));
        assertFalse(calculator.isOverdue(noDueDate, List.of(), TODAY));
    }

    @Test
    void statusBecomesPaidWhenBalanceReachesZeroAndBackToActive() {
        Debt debt = debt(1L, "1000.00", null, DebtStatus.ACTIVE);

        assertEquals(DebtStatus.PAID, calculator.statusAfterPayments(debt, List.of(payment(1L, "1000.00"))));

        debt.setStatus(DebtStatus.PAID);
        assertEquals(DebtStatus.ACTIVE, calculator.statusAfterPayments(debt, List.of(payment(1L, "400.00"))));
    }

    @Test
    void cancelledStatusIsKept() {
        Debt debt = debt(1L, "1000.00", null, DebtStatus.CANCELLED);

        assertEquals(DebtStatus.CANCELLED, calculator.statusAfterPayments(debt, List.of(payment(1L, "1000.00"))));
    }

    @Test
    void summaryIgnoresCancelledDebtsAndCountsOverdue() {
        List<Debt> debts = List.of(
                debt(1L, "100000.00", TODAY.minusDays(3), DebtStatus.ACTIVE),
                debt(2L, "50000.00", TODAY.plusDays(10), DebtStatus.ACTIVE),
                debt(3L, "20000.00", TODAY.minusDays(30), DebtStatus.PAID),
                debt(4L, "999999.00", TODAY.minusDays(30), DebtStatus.CANCELLED));
        List<Payment> payments = List.of(
                payment(1L, "40000.00"),
                payment(3L, "20000.00"));

        DashboardSummary summary = calculator.summarize(debts, payments, TODAY);

        assertEquals(new BigDecimal("170000.00"), summary.totalOriginal());
        assertEquals(new BigDecimal("60000.00"), summary.totalPaid());
        assertEquals(new BigDecimal("110000.00"), summary.totalRemaining());
        assertEquals(2, summary.activeCount());
        assertEquals(1, summary.overdueCount());
    }

    @Test
    void attentionListHasOverdueAndSoonDueActiveDebtsSortedByDueDate() {
        List<Debt> debts = List.of(
                debt(1L, "1000.00", TODAY.plusDays(10), DebtStatus.ACTIVE),
                debt(2L, "1000.00", TODAY.minusDays(5), DebtStatus.ACTIVE),
                debt(3L, "1000.00", TODAY.plusDays(15), DebtStatus.ACTIVE),
                debt(4L, "1000.00", TODAY.minusDays(1), DebtStatus.CANCELLED),
                debt(5L, "1000.00", TODAY.plusDays(2), DebtStatus.ACTIVE),
                debt(6L, "1000.00", null, DebtStatus.ACTIVE));
        List<Payment> payments = List.of(payment(5L, "1000.00"), payment(1L, "250.00"));

        List<DebtOverview> attention = calculator.needingAttention(debts, payments, TODAY);

        assertEquals(List.of(2L, 1L), attention.stream().map(overview -> overview.debt().getId()).toList());
        assertTrue(attention.get(0).overdue());
        assertEquals(new BigDecimal("750.00"), attention.get(1).remaining());
    }

    private static Debt debt(Long id, String amount, LocalDate dueDate, DebtStatus status) {
        Debt debt = new Debt();
        debt.setId(id);
        debt.setTitle("Debt " + id);
        debt.setCreditorName("Creditor");
        debt.setOriginalAmount(new BigDecimal(amount));
        debt.setDueDate(dueDate);
        debt.setStatus(status);
        return debt;
    }

    private static Payment payment(Long debtId, String amount) {
        Payment payment = new Payment();
        payment.setDebtId(debtId);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(TODAY);
        return payment;
    }
}
