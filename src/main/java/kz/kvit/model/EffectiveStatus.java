package kz.kvit.model;

import kz.kvit.i18n.I18n;

/**
 * Status as the user sees it: the stored status plus "overdue", which is never stored
 * because it depends on today's date. Same rules as effectiveStatus() on the web site.
 * Declared in the sort order of the status column: overdue first, cancelled last.
 */
public enum EffectiveStatus {
    OVERDUE,
    ACTIVE,
    PAID,
    CANCELLED;

    @Override
    public String toString() {
        return I18n.t("status." + name());
    }
}
