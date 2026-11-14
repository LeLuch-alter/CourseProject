package kz.kvit.model;

/** A payment together with the title, creditor and currency of its debt, for lists and reports. */
public record PaymentEntry(Payment payment, String debtTitle, String creditorName, String currency) {

    public PaymentEntry(Payment payment, String debtTitle, String creditorName) {
        this(payment, debtTitle, creditorName, Currencies.TENGE);
    }

    public boolean isInTenge() {
        return currency == null || Currencies.TENGE.equals(currency);
    }
}
