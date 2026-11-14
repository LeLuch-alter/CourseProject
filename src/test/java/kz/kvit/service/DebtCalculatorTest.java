package kz.kvit.service;

import kz.kvit.model.DashboardSummary;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtDirection;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.EffectiveStatus;
import kz.kvit.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    @Test
    void summarySeparatesWhatIOweFromWhatOthersOweMe() {
        Debt mine = debt(1L, "100000.00", TODAY.minusDays(3), DebtStatus.ACTIVE);
        Debt lent = debt(2L, "40000.00", TODAY.minusDays(3), DebtStatus.ACTIVE);
        lent.setDirection(DebtDirection.LENT);
        List<Payment> payments = List.of(payment(1L, "25000.00"), payment(2L, "15000.00"));

        DashboardSummary summary = calculator.summarize(List.of(mine, lent), payments, TODAY);

        assertEquals(new BigDecimal("100000.00"), summary.totalOriginal());
        assertEquals(new BigDecimal("25000.00"), summary.totalPaid());
        assertEquals(new BigDecimal("75000.00"), summary.totalRemaining());
        assertEquals(new BigDecimal("40000.00"), summary.lentTotal());
        assertEquals(new BigDecimal("25000.00"), summary.lentRemaining());
        assertEquals(new BigDecimal("15000.00"), summary.lentReturned());
        // A friend's overdue return is not "my" overdue debt.
        assertEquals(1, summary.activeCount());
        assertEquals(1, summary.overdueCount());
    }

    @Test
    void otherCurrenciesAreNeverAddedToTenge() {
        Debt tenge = debt(1L, "50000.00", null, DebtStatus.ACTIVE);
        Debt dollars = debt(2L, "1000.00", null, DebtStatus.ACTIVE);
        dollars.setCurrency("USD");
        Debt euros = debt(3L, "300.00", null, DebtStatus.ACTIVE);
        euros.setCurrency("EUR");
        euros.setDirection(DebtDirection.LENT);
        Debt paidDollars = debt(4L, "10.00", null, DebtStatus.PAID);
        paidDollars.setCurrency("USD");
        List<Payment> payments = List.of(payment(2L, "200.00"), payment(4L, "10.00"));

        DashboardSummary summary = calculator.summarize(List.of(tenge, dollars, euros, paidDollars), payments, TODAY);

        assertEquals(new BigDecimal("50000.00"), summary.totalOriginal());
        assertEquals(BigDecimal.ZERO, summary.totalPaid());
        assertEquals(Map.of("USD", new BigDecimal("800.00")), summary.foreignOwed());
        assertEquals(Map.of("EUR", new BigDecimal("300.00")), summary.foreignLent());
        assertEquals(BigDecimal.ZERO, summary.lentTotal());
        // Counters include my debts in every currency, like the web site.
        assertEquals(2, summary.activeCount());
    }

    @Test
    void dueSoonMeansActiveWithBalanceDueWithinFourteenDays() {
        Debt today = debt(1L, "100.00", TODAY, DebtStatus.ACTIVE);
        Debt inTwoWeeks = debt(2L, "100.00", TODAY.plusDays(14), DebtStatus.ACTIVE);
        Debt tooLate = debt(3L, "100.00", TODAY.plusDays(15), DebtStatus.ACTIVE);
        Debt alreadyOverdue = debt(4L, "100.00", TODAY.minusDays(1), DebtStatus.ACTIVE);

        assertTrue(calculator.isDueSoon(today, List.of(), TODAY));
        assertTrue(calculator.isDueSoon(inTwoWeeks, List.of(), TODAY));
        assertFalse(calculator.isDueSoon(tooLate, List.of(), TODAY));
        assertFalse(calculator.isDueSoon(alreadyOverdue, List.of(), TODAY));
        assertFalse(calculator.isDueSoon(today, List.of(payment(1L, "100.00")), TODAY));
    }

    @Test
    void effectiveStatusFollowsTheWebRules() {
        Debt active = debt(1L, "100.00", TODAY.plusDays(3), DebtStatus.ACTIVE);
        Debt overdue = debt(2L, "100.00", TODAY.minusDays(3), DebtStatus.ACTIVE);
        Debt cancelledOverdue = debt(3L, "100.00", TODAY.minusDays(3), DebtStatus.CANCELLED);
        Debt fullyPaidButActive = debt(4L, "100.00", TODAY.minusDays(3), DebtStatus.ACTIVE);

        assertEquals(EffectiveStatus.ACTIVE, calculator.effectiveStatus(active, List.of(), TODAY));
        assertEquals(EffectiveStatus.OVERDUE, calculator.effectiveStatus(overdue, List.of(), TODAY));
        assertEquals(EffectiveStatus.CANCELLED, calculator.effectiveStatus(cancelledOverdue, List.of(), TODAY));
        assertEquals(EffectiveStatus.PAID,
                calculator.effectiveStatus(fullyPaidButActive, List.of(payment(4L, "100.00")), TODAY));
    }

    @Test
    void remainingNeverGoesBelowZero() {
        Debt debt = debt(1L, "100.00", null, DebtStatus.ACTIVE);

        assertEquals(BigDecimal.ZERO, calculator.remainingBalance(debt, List.of(payment(1L, "150.00"))));
    }

    @Test
    void owedInTengeLeavesOutLentCancelledAndForeignDebts() {
        Debt mine = debt(1L, "100.00", null, DebtStatus.ACTIVE);
        Debt lent = debt(2L, "100.00", null, DebtStatus.ACTIVE);
        lent.setDirection(DebtDirection.LENT);
        Debt cancelled = debt(3L, "100.00", null, DebtStatus.CANCELLED);
        Debt rubles = debt(4L, "100.00", null, DebtStatus.ACTIVE);
        rubles.setCurrency("RUB");

        List<Debt> result = calculator.owedInTenge(List.of(mine, lent, cancelled, rubles));

        assertEquals(List.of(1L), result.stream().map(Debt::getId).toList());
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
