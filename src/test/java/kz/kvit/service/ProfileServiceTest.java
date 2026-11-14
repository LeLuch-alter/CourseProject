package kz.kvit.service;

import kz.kvit.model.ReminderMode;
import kz.kvit.model.ReminderSettings;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Only the validation and parsing, which need no database. */
class ProfileServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final ProfileService service = new ProfileService(null, null);

    @Test
    void parsesDaysAndTime() {
        assertEquals(14, service.parseDaysBefore(" 14 "));
        assertEquals(LocalTime.of(8, 0), service.parseTime("8:00"));
        assertEquals(LocalTime.of(21, 30), service.parseTime("21:30"));
        assertThrows(ValidationException.class, () -> service.parseDaysBefore("91"));
        assertThrows(ValidationException.class, () -> service.parseDaysBefore("-1"));
        assertThrows(ValidationException.class, () -> service.parseDaysBefore("abc"));
        assertThrows(ValidationException.class, () -> service.parseTime("25:00"));
        assertThrows(ValidationException.class, () -> service.parseTime(""));
    }

    @Test
    void weekdaysModeNeedsAtLeastOneValidDay() {
        assertThrows(ValidationException.class, () -> service.validate(settings(ReminderMode.WEEKDAYS, List.of(), List.of()), TODAY));
        assertThrows(ValidationException.class, () -> service.validate(settings(ReminderMode.WEEKDAYS, List.of(8), List.of()), TODAY));
        assertDoesNotThrow(() -> service.validate(settings(ReminderMode.WEEKDAYS, List.of(1, 5), List.of()), TODAY));
    }

    @Test
    void datesModeNeedsAFutureDate() {
        assertThrows(ValidationException.class, () -> service.validate(settings(ReminderMode.DATES, List.of(), List.of()), TODAY));
        assertThrows(ValidationException.class,
                () -> service.validate(settings(ReminderMode.DATES, List.of(), List.of(TODAY.minusDays(2))), TODAY));
        assertDoesNotThrow(() -> service.validate(settings(ReminderMode.DATES, List.of(), List.of(TODAY.plusDays(2))), TODAY));
    }

    @Test
    void olderDatabaseOnlyChecksTheDays() {
        ReminderSettings basic = new ReminderSettings(10, true, false, null, null, ReminderMode.WEEKDAYS, List.of(), List.of());
        assertDoesNotThrow(() -> service.validate(basic, TODAY));
    }

    private static ReminderSettings settings(ReminderMode mode, List<Integer> weekdays, List<LocalDate> dates) {
        return new ReminderSettings(14, false, true, LocalTime.of(8, 0), "Asia/Almaty", mode, weekdays, dates);
    }
}
