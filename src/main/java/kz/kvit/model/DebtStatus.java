package kz.kvit.model;

import kz.kvit.i18n.I18n;

public enum DebtStatus {
    ACTIVE,
    PAID,
    CANCELLED;

    /** Translated name for the screen, e.g. "Активный". */
    @Override
    public String toString() {
        return I18n.t("status." + name());
    }
}
