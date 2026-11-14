package kz.kvit.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Reminder preferences stored in the user's profile row.
 * The schedule fields (time, time zone, mode, weekdays, dates) come from supabase/schema_web_4.sql;
 * scheduleSupported is false when that script was not run yet, and then only the first two are used.
 *
 * @param weekdays ISO day numbers, 1 = Monday ... 7 = Sunday
 */
public record ReminderSettings(int daysBefore, boolean emailEnabled, boolean scheduleSupported,
                               LocalTime time, String timeZone, ReminderMode mode,
                               List<Integer> weekdays, List<LocalDate> dates) {
}
