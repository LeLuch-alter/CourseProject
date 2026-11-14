package kz.kvit.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One planned payment of a debt's schedule (table "installments").
 * paymentId is set once the instalment was paid: it points to the real payment record.
 */
public record Installment(Long id, long debtId, int seq, LocalDate dueDate, BigDecimal amount, Long paymentId) {

    public boolean isPaid() {
        return paymentId != null;
    }

    public boolean isLate(LocalDate today) {
        return !isPaid() && dueDate.isBefore(today);
    }
}
