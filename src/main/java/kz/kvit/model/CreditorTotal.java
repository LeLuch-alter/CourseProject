package kz.kvit.model;

import java.math.BigDecimal;

/** Totals for one creditor. A null name means "all other creditors" folded together. */
public record CreditorTotal(String creditorName, BigDecimal original, BigDecimal remaining, int debtCount) {
}
