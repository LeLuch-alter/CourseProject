package kz.kvit.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayoffPlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final PayoffPlanner planner = new PayoffPlanner();

    @Test
    void monthsAreRoundedUp() {
        PayoffPlanner.Plan plan = planner.planByMonthlyPayment(new BigDecimal("100000"), new BigDecimal("30000"), TODAY);

        assertEquals(4, plan.months());
        assertEquals(LocalDate.of(2027, 2, 25), plan.payoffDate());
    }

    @Test
    void exactDivisionNeedsNoExtraMonth() {
        assertEquals(5, planner.planByMonthlyPayment(new BigDecimal("50000"), new BigDecimal("10000"), TODAY).months());
    }

    @Test
    void nothingLeftMeansAlreadyPaid() {
        assertEquals(0, planner.planByMonthlyPayment(BigDecimal.ZERO, new BigDecimal("10"), TODAY).months());
    }

    @Test
    void monthlyPaymentMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.planByMonthlyPayment(new BigDecimal("100"), BigDecimal.ZERO, TODAY));
    }

    @Test
    void paymentToFinishByDateIsRoundedUpToWholeTenge() {
        // October -> January = 3 months; 100 000 / 3 = 33 333.33 -> 33 334
        assertEquals(new BigDecimal("33334"),
                planner.monthlyPaymentToFinishBy(new BigDecimal("100000"), LocalDate.of(2027, 1, 15), TODAY));
    }

    @Test
    void targetInThisMonthOrPastMeansPayEverythingNow() {
        assertEquals(new BigDecimal("5000"),
                planner.monthlyPaymentToFinishBy(new BigDecimal("5000"), TODAY.plusDays(2), TODAY));
        assertEquals(new BigDecimal("5000"),
                planner.monthlyPaymentToFinishBy(new BigDecimal("5000"), TODAY.minusMonths(2), TODAY));
    }
}
