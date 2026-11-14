package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceTest {
    private final NotificationService service = new NotificationService(null);

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(I18n.Language.RU);
    }

    @Test
    void describesNotificationsLikeTheWebSite() {
        I18n.setLanguage(I18n.Language.EN);

        assertEquals("proposes an IOU for 50000.00 T",
                service.describe(notification("loan_request", "50000.00", false), NotificationServiceTest::money));
        assertEquals("due 2026-11-01", service.describe(notification("due_soon", "2026-11-01", false), NotificationServiceTest::money));
        assertEquals("wants to be your friend", service.describe(notification("friend_request", "friend_request", false),
                NotificationServiceTest::money));
        assertEquals("something", service.describe(notification("unknown_kind", "something", false), NotificationServiceTest::money));
    }

    @Test
    void countsUnread() {
        List<Notification> list = List.of(notification("message", "hi", false), notification("message", "hi", true));

        assertEquals(1, service.unreadCount(list));
    }

    private static String money(BigDecimal amount) {
        return amount.toPlainString() + " T";
    }

    private static Notification notification(String kind, String body, boolean read) {
        return new Notification(1L, kind, "Aigerim", body, null, read, OffsetDateTime.parse("2026-10-25T08:00:00Z"));
    }
}
