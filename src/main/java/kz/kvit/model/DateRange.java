package kz.kvit.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** An inclusive range of days. The start is never after the end. */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
    }

    /** Range between two clicked days, in whichever order they were clicked. */
    public static DateRange between(LocalDate first, LocalDate second) {
        return new DateRange(first, second);
    }

    public static DateRange day(LocalDate day) {
        return new DateRange(day, day);
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }

    public boolean isSingleDay() {
        return start.equals(end);
    }

    public long lengthInDays() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}
