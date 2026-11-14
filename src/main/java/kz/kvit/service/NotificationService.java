package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Notification;
import kz.kvit.repository.NotificationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** In-app notifications written by the database (loan requests, due-date reminders and so on). */
public class NotificationService {
    static final int LIST_LIMIT = 50;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** The latest notifications, newest first. */
    public List<Notification> getLatest() {
        return repository.findLatest(LIST_LIMIT);
    }

    public int unreadCount(List<Notification> notifications) {
        return (int) notifications.stream().filter(notification -> !notification.read()).count();
    }

    public void markAllRead(List<Notification> notifications) {
        repository.markRead(notifications.stream()
                .filter(notification -> !notification.read())
                .map(Notification::id)
                .toList());
    }

    /**
     * Second line of a notification, like describe() in the web site's Shell.tsx.
     * The title already holds the other person's name (or the debt title for "due_soon").
     * For loans and split bills the body is an amount in tenge.
     *
     * @param formatMoney turns that amount into text, e.g. "50 000,00 ₸"
     */
    public String describe(Notification notification, Function<BigDecimal, String> formatMoney) {
        String body = notification.body() == null ? "" : notification.body();
        return switch (notification.kind()) {
            case "friend_request", "friend_accepted", "room" -> I18n.t("notify." + notification.kind());
            case "loan_request", "loan_active", "loan_declined", "loan_cancelled", "loan_repaid", "split" ->
                    I18n.t("notify." + notification.kind(), money(body, formatMoney));
            case "message", "due_soon" -> I18n.t("notify." + notification.kind(), body);
            default -> body;
        };
    }

    private static String money(String body, Function<BigDecimal, String> formatMoney) {
        try {
            return formatMoney.apply(new BigDecimal(body));
        } catch (NumberFormatException e) {
            // Not a number (an older row): show the text as it is.
            return body;
        }
    }
}
