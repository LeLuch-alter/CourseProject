package kz.kvit.model;

import kz.kvit.i18n.I18n;

/** Values of loans.status in the database. */
public enum LoanStatus {
    PENDING,
    ACTIVE,
    DECLINED,
    CANCELLED,
    REPAID;

    @Override
    public String toString() {
        return I18n.t("loans.status." + name());
    }

    public boolean isClosed() {
        return this == DECLINED || this == CANCELLED || this == REPAID;
    }
}
