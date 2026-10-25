package kz.kvit.model;

import java.math.BigDecimal;

public record DashboardSummary(
        BigDecimal totalOriginal,
        BigDecimal totalPaid,
        BigDecimal totalRemaining,
        int activeCount,
        int overdueCount) {
}
