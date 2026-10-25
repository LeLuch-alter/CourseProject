package kz.kvit.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {
    private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

    @Test
    void orderOfClicksDoesNotMatter() {
        assertEquals(DateRange.between(OCT_10, OCT_20), DateRange.between(OCT_20, OCT_10));
        assertEquals(OCT_10, DateRange.between(OCT_20, OCT_10).start());
    }

    @Test
    void bothEndsAreIncluded() {
        DateRange range = DateRange.between(OCT_10, OCT_20);

        assertTrue(range.contains(OCT_10));
        assertTrue(range.contains(OCT_20));
        assertFalse(range.contains(OCT_20.plusDays(1)));
        assertFalse(range.contains(OCT_10.minusDays(1)));
        assertEquals(11, range.lengthInDays());
    }

    @Test
    void singleDayRange() {
        DateRange day = DateRange.day(OCT_10);

        assertTrue(day.isSingleDay());
        assertEquals(1, day.lengthInDays());
        assertFalse(DateRange.between(OCT_10, OCT_20).isSingleDay());
    }
}
