package kz.kvit.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rough estimate of when the debts will be paid off at the recent payment pace. */
public record PayoffForecast(BigDecimal averageMonthlyPayment, int monthsLeft, LocalDate estimatedDate) {
}
