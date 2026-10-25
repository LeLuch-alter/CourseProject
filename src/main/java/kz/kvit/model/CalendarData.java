package kz.kvit.model;

import java.util.List;

/** Debts (for their due dates) and payments, for the calendar screen. */
public record CalendarData(List<DebtOverview> debts, List<PaymentEntry> payments) {
}
