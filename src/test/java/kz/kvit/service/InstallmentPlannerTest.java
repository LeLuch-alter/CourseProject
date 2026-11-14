package kz.kvit.service;

import kz.kvit.model.Installment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallmentPlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final InstallmentPlanner planner = new InstallmentPlanner();

    @Test
    void lastPartTakesTheRoundingRemainder() {
        List<BigDecimal> parts = planner.splitEqually(new BigDecimal("100.00"), 3);

        assertEquals(List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34")), parts);
        assertEquals(new BigDecimal("100.00"), parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void onePartIsTheWholeBalance() {
        assertEquals(List.of(new BigDecimal("999.99")), planner.splitEqually(new BigDecimal("999.99"), 1));
    }

    @Test
    void scheduleIsMonthlyAndKeepsShortMonthsInside() {
        List<Installment> schedule = planner.buildSchedule(7L, new BigDecimal("300.00"), 3,
                LocalDate.of(2027, 1, 31), TODAY);

        assertEquals(List.of(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
                schedule.stream().map(Installment::dueDate).toList());
        assertEquals(List.of(1, 2, 3), schedule.stream().map(Installment::seq).toList());
        assertEquals(7L, schedule.get(0).debtId());
    }

    @Test
    void rejectsBadInput() {
        BigDecimal balance = new BigDecimal("100.00");
        assertThrows(ValidationException.class, () -> planner.buildSchedule(1L, balance, 0, TODAY, TODAY));
        assertThrows(ValidationException.class, () -> planner.buildSchedule(1L, balance, 61, TODAY, TODAY));
        assertThrows(ValidationException.class, () -> planner.buildSchedule(1L, BigDecimal.ZERO, 3, TODAY, TODAY));
        assertThrows(ValidationException.class, () -> planner.buildSchedule(1L, balance, 3, TODAY.minusDays(1), TODAY));
        assertThrows(ValidationException.class, () -> planner.buildSchedule(1L, new BigDecimal("0.02"), 3, TODAY, TODAY));
        assertThrows(ValidationException.class, () -> planner.parseParts("три"));
    }

    @Test
    void lateOnlyWhenUnpaidAndPastDue() {
        Installment unpaid = new Installment(1L, 1L, 1, TODAY.minusDays(1), BigDecimal.TEN, null);
        Installment paid = new Installment(2L, 1L, 2, TODAY.minusDays(1), BigDecimal.TEN, 55L);
        Installment future = new Installment(3L, 1L, 3, TODAY, BigDecimal.TEN, null);

        assertEquals(true, unpaid.isLate(TODAY));
        assertEquals(false, paid.isLate(TODAY));
        assertEquals(false, future.isLate(TODAY));
    }
}
