package kz.kvit.model;

import kz.kvit.i18n.I18n;

/** When reminders are sent. The database stores the lower-case name ('daily', 'weekdays', 'dates'). */
public enum ReminderMode {
    DAILY,
    WEEKDAYS,
    DATES;

    public String databaseValue() {
        return name().toLowerCase();
    }

    public static ReminderMode fromDatabase(String value) {
        for (ReminderMode mode : values()) {
            if (mode.databaseValue().equals(value)) {
                return mode;
            }
        }
        return DAILY;
    }

    @Override
    public String toString() {
        return I18n.t("reminders.mode." + name());
    }
}
