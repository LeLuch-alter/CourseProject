package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.i18n.I18n;
import kz.kvit.model.ReminderMode;
import kz.kvit.model.ReminderSettings;
import kz.kvit.model.TelegramStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads and updates the "profiles" table (reminder settings, display names of friends)
 * and reads the user's row of "telegram_links". Both tables come from the web SQL scripts.
 */
public class ProfileRepository {
    private static final String PROFILES = "/rest/v1/profiles";
    private static final String TELEGRAM = "/rest/v1/telegram_links";
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);
    private static final String DEFAULT_TIME_ZONE = "Asia/Almaty";

    private final SupabaseClient client;

    public ProfileRepository(SupabaseClient client) {
        this.client = client;
    }

    /**
     * Reads "select *" so that a database without the newer reminder columns still works:
     * missing columns simply are not in the row, and scheduleSupported becomes false.
     */
    public ReminderSettings findReminderSettings(String userId) {
        JsonNode row = DebtRepository.singleRow(client.get(PROFILES + "?select=*&id=eq." + userId));
        if (!row.has("reminder_days")) {
            // schema_web_2.sql was not run: there are no reminder settings at all.
            throw new DataAccessException(I18n.t("error.db.missingColumn"), "42703");
        }
        boolean scheduleSupported = row.has("reminder_time") && row.has("reminder_mode");

        List<Integer> weekdays = new ArrayList<>();
        for (JsonNode day : row.path("reminder_weekdays")) {
            weekdays.add(day.asInt());
        }
        List<LocalDate> dates = new ArrayList<>();
        for (JsonNode date : row.path("reminder_dates")) {
            dates.add(LocalDate.parse(date.asText()));
        }
        // Postgres "time" arrives as "08:00:00".
        LocalTime time = row.hasNonNull("reminder_time")
                ? LocalTime.parse(row.get("reminder_time").asText())
                : DEFAULT_TIME;

        return new ReminderSettings(
                row.path("reminder_days").asInt(14),
                row.path("email_reminders").asBoolean(false),
                scheduleSupported,
                time,
                row.path("reminder_tz").asText(DEFAULT_TIME_ZONE),
                ReminderMode.fromDatabase(row.path("reminder_mode").asText("daily")),
                weekdays,
                dates);
    }

    /** Sends only the columns that exist, so an older database is not asked for unknown ones. */
    public void updateReminderSettings(String userId, ReminderSettings settings) {
        ObjectNode body = client.createObject();
        body.put("reminder_days", settings.daysBefore());
        body.put("email_reminders", settings.emailEnabled());
        if (settings.scheduleSupported()) {
            body.put("reminder_time", settings.time().toString());
            body.put("reminder_tz", settings.timeZone());
            body.put("reminder_mode", settings.mode().databaseValue());
            ArrayNode weekdays = body.putArray("reminder_weekdays");
            settings.weekdays().forEach(weekdays::add);
            ArrayNode dates = body.putArray("reminder_dates");
            settings.dates().forEach(date -> dates.add(date.toString()));
        }
        DebtRepository.singleRow(client.patch(PROFILES + "?id=eq." + userId, body));
    }

    /** Display names by user id; ids without a profile are left out. */
    public Map<String, String> findDisplayNames(Collection<String> userIds) {
        Map<String, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        String idList = userIds.stream().distinct().collect(Collectors.joining(","));
        JsonNode rows = client.get(PROFILES + "?select=id,display_name,username&id=in.(" + idList + ")");
        for (JsonNode row : rows) {
            String name = row.path("display_name").asText("");
            names.put(row.get("id").asText(), name.isBlank() ? "@" + row.path("username").asText() : name);
        }
        return names;
    }

    public TelegramStatus findTelegramStatus(String userId) {
        try {
            JsonNode rows = client.get(TELEGRAM + "?select=chat_id&user_id=eq." + userId);
            boolean linked = rows.isArray() && !rows.isEmpty() && rows.get(0).hasNonNull("chat_id");
            return linked ? TelegramStatus.LINKED : TelegramStatus.NOT_LINKED;
        } catch (DataAccessException e) {
            if (e.isMissingSchema()) {
                return TelegramStatus.UNAVAILABLE;
            }
            throw e;
        }
    }
}
