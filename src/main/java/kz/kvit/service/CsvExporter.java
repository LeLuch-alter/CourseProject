package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.PaymentEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Builds CSV text that opens correctly in Excel: ";" as separator (Excel's default for
 * Russian/Kazakh Windows), a UTF-8 BOM so Cyrillic is not garbled, and decimal commas for RU/KZ.
 */
public class CsvExporter {
    private static final String BOM = "\uFEFF";
    private static final String SEPARATOR = ";";

    public String debts(List<DebtOverview> debts) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, I18n.t("debts.col.title"), I18n.t("debts.col.creditor"), I18n.t("debts.col.amount"),
                I18n.t("debts.col.paid"), I18n.t("debts.col.remaining"), I18n.t("debts.col.due"),
                I18n.t("debts.col.status"), I18n.t("form.notes"));
        for (DebtOverview overview : debts) {
            row(csv, overview.debt().getTitle(), overview.debt().getCreditorName(),
                    number(overview.debt().getOriginalAmount()), number(overview.paid()),
                    number(overview.remaining()), date(overview.debt().getDueDate()),
                    overview.debt().getStatus().toString(), overview.debt().getNotes());
        }
        return csv.toString();
    }

    public String payments(List<PaymentEntry> payments) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, I18n.t("payments.col.date"), I18n.t("payments.col.debt"), I18n.t("debts.col.creditor"),
                I18n.t("payments.col.amount"), I18n.t("form.notes"));
        for (PaymentEntry entry : payments) {
            row(csv, date(entry.payment().getPaymentDate()), entry.debtTitle(), entry.creditorName(),
                    number(entry.payment().getAmount()), entry.payment().getNotes());
        }
        return csv.toString();
    }

    private void row(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(escape(values[i]));
        }
        csv.append("\r\n");
    }

    /** Values with the separator, quotes or line breaks are wrapped in quotes; inner quotes are doubled. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String number(BigDecimal value) {
        String plain = value.toPlainString();
        return I18n.getLanguage() == I18n.Language.EN ? plain : plain.replace('.', ',');
    }

    private String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }
}
