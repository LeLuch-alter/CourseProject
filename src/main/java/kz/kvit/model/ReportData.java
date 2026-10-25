package kz.kvit.model;

import java.util.List;

/**
 * Data for the reports screen.
 * forecast is null when there were no payments recently or nothing is left to pay.
 */
public record ReportData(
        List<MonthTotal> paymentsByMonth,
        List<CreditorTotal> byCreditor,
        StatusBreakdown statuses,
        DashboardSummary summary,
        PayoffForecast forecast) {
}
