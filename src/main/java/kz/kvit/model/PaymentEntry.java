package kz.kvit.model;

/** A payment together with the title and creditor of its debt, for lists and reports. */
public record PaymentEntry(Payment payment, String debtTitle, String creditorName) {
}
