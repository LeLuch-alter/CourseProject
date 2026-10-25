package kz.kvit.model;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthTotal(YearMonth month, BigDecimal total) {
}
