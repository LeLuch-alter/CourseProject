package kz.kvit.model;

import java.math.BigDecimal;

/** A debt together with its calculated payment totals. */
public record DebtOverview(Debt debt, BigDecimal paid, BigDecimal remaining, boolean overdue) {

    /** Cancelled, paid (by status or because nothing is left), overdue or active — in that order. */
    public EffectiveStatus effectiveStatus() {
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            return EffectiveStatus.CANCELLED;
        }
        if (debt.getStatus() == DebtStatus.PAID || remaining.signum() <= 0) {
            return EffectiveStatus.PAID;
        }
        return overdue ? EffectiveStatus.OVERDUE : EffectiveStatus.ACTIVE;
    }

    /** True while payments can still be recorded for the debt. */
    public boolean isOpen() {
        EffectiveStatus status = effectiveStatus();
        return status == EffectiveStatus.ACTIVE || status == EffectiveStatus.OVERDUE;
    }
}
