package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.model.Notification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Reads the "notifications" table and marks rows as read. RLS returns only the user's own rows. */
public class NotificationRepository {
    private static final String TABLE = "/rest/v1/notifications";

    private final SupabaseClient client;

    public NotificationRepository(SupabaseClient client) {
        this.client = client;
    }

    public List<Notification> findLatest(int limit) {
        JsonNode rows = client.get(TABLE + "?select=*&order=created_at.desc&limit=" + limit);
        List<Notification> result = new ArrayList<>();
        for (JsonNode row : rows) {
            result.add(new Notification(
                    row.get("id").asLong(),
                    row.path("kind").asText(""),
                    row.path("title").asText(""),
                    DebtRepository.textOrNull(row, "body"),
                    DebtRepository.textOrNull(row, "link"),
                    row.path("read").asBoolean(false),
                    OffsetDateTime.parse(row.get("created_at").asText())));
        }
        return result;
    }

    public void markRead(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        ObjectNode body = client.createObject().put("read", true);
        client.patch(TABLE + "?id=in.(" + idList + ")", body);
    }
}
