package kz.kvit.model;

/** Whether the user connected the Telegram bot on the web site (table "telegram_links"). */
public enum TelegramStatus {
    LINKED,
    NOT_LINKED,
    /** The table does not exist in this database yet. */
    UNAVAILABLE
}
