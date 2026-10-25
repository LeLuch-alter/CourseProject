package kz.kvit.service;

import kz.kvit.model.CreditorTotal;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.MonthTotal;
import kz.kvit.model.Payment;
import kz.kvit.model.PayoffForecast;
import kz.kvit.model.StatusBreakdown;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final ReportCalculator calculator = new ReportCalculator();

    @Test
    void paymentsByMonthCoversEveryMonthIncludingEmptyOnes() {
        List<Payment> payments = List.of(
                payment(1L, "100.00", LocalDate.of(2026, 10, 3)),
                payment(1L, "50.00", LocalDate.of(2026, 10, 20)),
                payment(1L, "70.00", LocalDate.of(2026, 8, 1)),
                payment(1L, "999.00", LocalDate.of(2025, 1, 1)));

        List<MonthTotal> months = calculator.paymentsByMonth(payments, YearMonth.from(TODAY), 3);

        assertEquals(List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
                months.stream().map(MonthTotal::month).toList());
        assertEquals(new BigDecimal("70.00"), months.get(0).total());
        assertEquals(BigDecimal.ZERO, months.get(1).total());
        assertEquals(new BigDecimal("150.00"), months.get(2).total());
    }

    @Test
    void creditorsAreMergedIgnoringCaseAndSortedByRemaining() {
        List<Debt> debts = List.of(
                debt(1L, "Kaspi Bank", "1000.00", DebtStatus.ACTIVE, null),
                debt(2L, "kaspi bank ", "500.00", DebtStatus.ACTIVE, null),
                debt(3L, "Айдос", "2000.00", DebtStatus.ACTIVE, null),
                debt(4L, "Halyk", "9000.00", DebtStatus.CANCELLED, null));
        List<Payment> payments = List.of(payment(3L, "1000.00", TODAY));

        List<CreditorTotal> totals = calculator.byCreditor(debts, payments);

        assertEquals(2, totals.size());
        assertEquals("Kaspi Bank", totals.get(0).creditorName());
        assertEquals(new BigDecimal("1500.00"), totals.get(0).remaining());
        assertEquals(2, totals.get(0).debtCount());
        assertEquals(new BigDecimal("1000.00"), totals.get(1).remaining());
    }

    @Test
    void fullyPaidCreditorsAreLeftOut() {
        List<Debt> debts = List.of(debt(1L, "Paid", "100.00", DebtStatus.PAID, null), debt(2L, "Open", "100.00", DebtStatus.ACTIVE, null));
        List<CreditorTotal> totals = calculator.byCreditor(debts, List.of(payment(1L, "100.00", TODAY)));

        assertEquals(List.of("Open"), totals.stream().map(CreditorTotal::creditorName).toList());
    }

    @Test
    void creditorsBeyondTheLimitAreFoldedIntoOther() {
        List<Debt> debts = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            debts.add(debt(i, "Creditor " + i, i + "00.00", DebtStatus.ACTIVE, null));
        }

        List<CreditorTotal> totals = calculator.byCreditor(debts, List.of());

        assertEquals(ReportCalculator.MAX_CREDITORS, totals.size());
        CreditorTotal other = totals.get(totals.size() - 1);
        assertNull(other.creditorName());
        // Creditors 1, 2 and 3 are the smallest: 100 + 200 + 300.
        assertEquals(new BigDecimal("600.00"), other.remaining());
        assertEquals(3, other.debtCount());
    }

    @Test
    void statusesSeparateOverdueFromActive() {
        List<Debt> debts = List.of(
                debt(1L, "A", "100.00", DebtStatus.ACTIVE, TODAY.minusDays(1)),
                debt(2L, "B", "100.00", DebtStatus.ACTIVE, TODAY.plusDays(1)),
                debt(3L, "C", "100.00", DebtStatus.PAID, TODAY.minusDays(9)),
                debt(4L, "D", "100.00", DebtStatus.CANCELLED, null));

        StatusBreakdown statuses = calculator.statuses(debts, List.of(), TODAY);

        assertEquals(new StatusBreakdown(1, 1, 1, 1), statuses);
        assertEquals(4, statuses.total());
    }

    @Test
    void forecastUsesAverageOfLastThreeMonths() {
        List<Payment> payments = List.of(
                payment(1L, "30000.00", TODAY.minusDays(10)),
                payment(1L, "30000.00", TODAY.minusDays(40)),
                payment(1L, "30000.00", TODAY.minusDays(70)),
                payment(1L, "99999.00", TODAY.minusMonths(5)));

        PayoffForecast forecast = calculator.forecast(new BigDecimal("100000.00"), payments, TODAY);

        assertEquals(new BigDecimal("30000.00"), forecast.averageMonthlyPayment());
        assertEquals(4, forecast.monthsLeft());
        assertEquals(TODAY.plusMonths(4), forecast.estimatedDate());
    }

    @Test
    void noForecastWithoutRecentPaymentsOrWhenNothingIsLeft() {
        assertNull(calculator.forecast(new BigDecimal("1000.00"), List.of(payment(1L, "10.00", TODAY.minusYears(1))), TODAY));
        assertNull(calculator.forecast(BigDecimal.ZERO, List.of(payment(1L, "10.00", TODAY)), TODAY));
    }

    private static Debt debt(Long id, String creditor, String amount, DebtStatus status, LocalDate dueDate) {
        Debt debt = new Debt();
        debt.setId(id);
        debt.setTitle("Debt " + id);
        debt.setCreditorName(creditor);
        debt.setOriginalAmount(new BigDecimal(amount));
        debt.setStatus(status);
        debt.setDueDate(dueDate);
        return debt;
    }

    private static Payment payment(Long debtId, String amount, LocalDate date) {
        Payment payment = new Payment();
        payment.setDebtId(debtId);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        return payment;
    }
}
