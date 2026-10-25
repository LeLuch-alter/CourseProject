package kz.kvit.model;

import java.util.List;

/** Everything the dashboard screen shows, loaded in one go. */
public record DashboardData(
        DashboardSummary summary,
        List<DebtOverview> needingAttention,
        List<PaymentEntry> recentPayments) {
}
