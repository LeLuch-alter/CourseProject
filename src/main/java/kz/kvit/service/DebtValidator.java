package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Debt;
import kz.kvit.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input checks for debts and payments. The same limits are enforced again by the
 * database (see supabase/schema.sql), so bypassing the app does not bypass the rules.
 */
public class DebtValidator {

    // Matches numeric(14, 2) in the database.
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    static final int MAX_TEXT_LENGTH = 200;
    static final int MAX_NOTES_LENGTH = 1000;

    /** Parses "1 500,50" or "1500.50". Returns null for an empty optional field. */
    public BigDecimal parseAmount(String text, String fieldName, boolean required) {
        // Removes ordinary and non-breaking spaces used as thousands separators.
        String cleaned = text == null ? "" : text.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.');
        if (cleaned.isEmpty()) {
            if (required) {
                throw new ValidationException(I18n.t("error.required", fieldName));
            }
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new ValidationException(I18n.t("error.notNumber", fieldName));
        }
    }

    public void validateDebt(Debt debt, BigDecimal alreadyPaid) {
        requireText(debt.getTitle(), I18n.t("form.title"));
        requireText(debt.getCreditorName(), I18n.t("form.creditor"));
        checkAmount(debt.getOriginalAmount(), I18n.t("form.amount"));

        if (debt.getInterestRate() != null) {
            BigDecimal rate = debt.getInterestRate();
            if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
                throw new ValidationException(I18n.t("error.rateRange"));
            }
            if (rate.scale() > 2) {
                throw new ValidationException(I18n.t("error.decimals", I18n.t("form.rate")));
            }
        }
        if (debt.getStatus() == null) {
            throw new ValidationException(I18n.t("error.status"));
        }
        checkNotes(debt.getNotes());

        if (alreadyPaid != null && debt.getOriginalAmount().compareTo(alreadyPaid) < 0) {
            throw new ValidationException(I18n.t("error.amountBelowPaid", alreadyPaid.toPlainString()));
        }
    }

    public void validatePayment(Payment payment, BigDecimal remainingBalance, LocalDate today) {
        if (payment.getDebtId() == null) {
            throw new ValidationException(I18n.t("error.payment.noDebt"));
        }
        checkAmount(payment.getAmount(), I18n.t("form.paymentAmount"));
        if (payment.getAmount().compareTo(remainingBalance) > 0) {
            throw new ValidationException(I18n.t("error.payment.tooBig", remainingBalance.toPlainString()));
        }
        if (payment.getPaymentDate() == null) {
            throw new ValidationException(I18n.t("error.payment.noDate"));
        }
        if (payment.getPaymentDate().isAfter(today)) {
            throw new ValidationException(I18n.t("error.payment.future"));
        }
        checkNotes(payment.getNotes());
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(I18n.t("error.required", fieldName));
        }
        if (value.trim().length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(I18n.t("error.tooLong", fieldName, MAX_TEXT_LENGTH));
        }
    }

    private void checkAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new ValidationException(I18n.t("error.required", fieldName));
        }
        if (amount.signum() <= 0) {
            throw new ValidationException(I18n.t("error.positive", fieldName));
        }
        if (amount.scale() > 2) {
            throw new ValidationException(I18n.t("error.decimals", fieldName));
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException(I18n.t("error.tooBig", fieldName));
        }
    }

    private void checkNotes(String notes) {
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new ValidationException(I18n.t("error.notesTooLong", MAX_NOTES_LENGTH));
        }
    }
}
