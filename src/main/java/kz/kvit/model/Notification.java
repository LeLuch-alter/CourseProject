package kz.kvit.model;

import java.time.OffsetDateTime;

/**
 * A row of the "notifications" table. The database writes them (triggers and the reminder job);
 * the app only reads them and marks them as read.
 * kind is e.g. "loan_request", "due_soon"; title is usually the name of the other person.
 */
public record Notification(long id, String kind, String title, String body, String link,
                           boolean read, OffsetDateTime createdAt) {
}
