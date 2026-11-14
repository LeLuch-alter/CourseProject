package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.ReminderMode;
import kz.kvit.model.ReminderSettings;
import kz.kvit.model.TelegramStatus;
import kz.kvit.repository.ProfileRepository;
import kz.kvit.repository.SupabaseClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Reminder settings and the Telegram link status of the current user.
 * Reminders themselves are sent by the server (see supabase/functions/due-reminders),
 * the app only edits the preferences in the profile.
 */
public class ProfileService {
    static final int MAX_DAYS_BEFORE = 90;
    static final int MAX_DATES = 60;

    private final ProfileRepository profileRepository;
    private final SupabaseClient client;

    public ProfileService(ProfileRepository profileRepository, SupabaseClient client) {
        this.profileRepository = profileRepository;
        this.client = client;
    }

    public ReminderSettings getReminderSettings() {
        return profileRepository.findReminderSettings(client.getCurrentUserId());
    }

    /**
     * The time zone is always this computer's zone, the same as the web site uses the browser's:
     * the server needs it to know which "08:00" is meant.
     */
    public void saveReminderSettings(ReminderSettings settings) {
        validate(settings, LocalDate.now());
        ReminderSettings withZone = new ReminderSettings(settings.daysBefore(), settings.emailEnabled(),
                settings.scheduleSupported(), settings.time(), ZoneId.systemDefault().getId(),
                settings.mode(), List.copyOf(settings.weekdays()),
                settings.dates().stream().sorted().distinct().toList());
        profileRepository.updateReminderSettings(client.getCurrentUserId(), withZone);
    }

    public TelegramStatus getTelegramStatus() {
        return profileRepository.findTelegramStatus(client.getCurrentUserId());
    }

    /** "14" -> 14; anything outside 0..90 is rejected with a readable message. */
    public int parseDaysBefore(String text) {
        try {
            int days = Integer.parseInt(text == null ? "" : text.trim());
            if (days >= 0 && days <= MAX_DAYS_BEFORE) {
                return days;
            }
        } catch (NumberFormatException e) {
            // Falls through to the same message as an out-of-range number.
        }
        throw new ValidationException(I18n.t("reminders.error.days", MAX_DAYS_BEFORE));
    }

    /** "8:00" or "08:00" -> 08:00. */
    public LocalTime parseTime(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.matches("\\d:\\d{2}")) {
            cleaned = "0" + cleaned;
        }
        try {
            return LocalTime.parse(cleaned);
        } catch (DateTimeParseException e) {
            throw new ValidationException(I18n.t("reminders.error.time"));
        }
    }

    /** Same checks as the settings page of the web site and the database constraints. */
    void validate(ReminderSettings settings, LocalDate today) {
        if (settings.daysBefore() < 0 || settings.daysBefore() > MAX_DAYS_BEFORE) {
            throw new ValidationException(I18n.t("reminders.error.days", MAX_DAYS_BEFORE));
        }
        if (!settings.scheduleSupported()) {
            return;
        }
        if (settings.time() == null) {
            throw new ValidationException(I18n.t("reminders.error.time"));
        }
        if (settings.mode() == ReminderMode.WEEKDAYS && settings.weekdays().isEmpty()) {
            throw new ValidationException(I18n.t("reminders.error.weekdays"));
        }
        for (int day : settings.weekdays()) {
            if (day < 1 || day > 7) {
                throw new ValidationException(I18n.t("reminders.error.weekdays"));
            }
        }
        if (settings.mode() == ReminderMode.DATES && settings.dates().isEmpty()) {
            throw new ValidationException(I18n.t("reminders.error.dates"));
        }
        if (settings.dates().size() > MAX_DATES) {
            throw new ValidationException(I18n.t("reminders.error.tooManyDates", MAX_DATES));
        }
        // Only newly added dates are checked against today in the form; past dates that are already
        // stored are harmless (the server simply never reaches them), so they are not rejected here.
        if (settings.mode() == ReminderMode.DATES && settings.dates().stream().allMatch(date -> date.isBefore(today))) {
            throw new ValidationException(I18n.t("reminders.error.dates"));
        }
    }
}
