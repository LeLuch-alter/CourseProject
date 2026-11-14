package kz.kvit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A loan between two friends (table "loans"), created on the web site.
 * The initiator creates the request; the other party confirms or declines it.
 */
public record Loan(String id, String lenderId, String borrowerId, String initiatorId,
                   BigDecimal amount, String purpose, LocalDate dueDate, String borrowerCard,
                   BigDecimal interestRate, LoanStatus status, String receiptNo, OffsetDateTime createdAt) {
}
