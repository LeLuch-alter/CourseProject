package kz.kvit.model;

import kz.kvit.i18n.I18n;

/**
 * Who owes whom. Values match the "direction" column of the debts table.
 * OWED = I owe the creditor; LENT = the other person owes me.
 */
public enum DebtDirection {
    OWED,
    LENT;

    /** Translated name for the screen, e.g. "Я должен". */
    @Override
    public String toString() {
        return I18n.t("debt.direction." + name());
    }
}
