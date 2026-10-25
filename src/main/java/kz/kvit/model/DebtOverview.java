package kz.kvit.model;

import java.math.BigDecimal;

/** A debt together with its calculated payment totals. */
public record DebtOverview(Debt debt, BigDecimal paid, BigDecimal remaining, boolean overdue) {
}
