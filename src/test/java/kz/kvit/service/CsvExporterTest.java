package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Payment;
import kz.kvit.model.PaymentEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExporterTest {

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(I18n.Language.RU);
    }

    @Test
    void valuesWithSeparatorsOrQuotesAreQuoted() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a;b\"", CsvExporter.escape("a;b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""));
        assertEquals("\"two\nlines\"", CsvExporter.escape("two\nlines"));
        assertEquals("", CsvExporter.escape(null));
    }

    @Test
    void paymentsCsvHasBomHeaderAndDecimalComma() {
        I18n.setLanguage(I18n.Language.RU);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("1500.50"));
        payment.setPaymentDate(LocalDate.of(2026, 10, 1));
        payment.setNotes("Платёж; первый");

        String csv = new CsvExporter().payments(List.of(new PaymentEntry(payment, "Кредит", "Kaspi Bank")));
        String[] lines = csv.split("\r\n");

        assertTrue(csv.startsWith("\uFEFF"));
        assertEquals(2, lines.length);
        assertEquals("2026-10-01;Кредит;Kaspi Bank;1500,50;\"Платёж; первый\"", lines[1]);
    }

    @Test
    void englishUsesDecimalPoint() {
        I18n.setLanguage(I18n.Language.EN);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.25"));
        payment.setPaymentDate(LocalDate.of(2026, 1, 2));

        String csv = new CsvExporter().payments(List.of(new PaymentEntry(payment, "Loan", "Bank")));

        assertTrue(csv.contains(";10.25;"));
    }
}
