package kz.kvit.service;

import kz.kvit.model.Debt;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DebtValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final DebtValidator validator = new DebtValidator();

    @Test
    void parsesAmountsWithSpacesAndComma() {
        assertEquals(new BigDecimal("1500.50"), validator.parseAmount("1 500,50", "Сумма", true));
        assertEquals(new BigDecimal("350000"), validator.parseAmount("350\u00A0000", "Сумма", true));
        assertNull(validator.parseAmount("  ", "Ставка", false));
    }

    @Test
    void rejectsNonNumericOrMissingAmount() {
        assertThrows(ValidationException.class, () -> validator.parseAmount("abc", "Сумма", true));
        assertThrows(ValidationException.class, () -> validator.parseAmount("", "Сумма", true));
    }

    @Test
    void validDebtPasses() {
        assertDoesNotThrow(() -> validator.validateDebt(validDebt(), BigDecimal.ZERO));
    }

    @Test
    void debtRequiresTitleCreditorAndPositiveAmount() {
        Debt noTitle = validDebt();
        noTitle.setTitle("   ");
        Debt noCreditor = validDebt();
        noCreditor.setCreditorName(null);
        Debt zeroAmount = validDebt();
        zeroAmount.setOriginalAmount(BigDecimal.ZERO);
        Debt tooManyDecimals = validDebt();
        tooManyDecimals.setOriginalAmount(new BigDecimal("10.555"));

        assertThrows(ValidationException.class, () -> validator.validateDebt(noTitle, null));
        assertThrows(ValidationException.class, () -> validator.validateDebt(noCreditor, null));
        assertThrows(ValidationException.class, () -> validator.validateDebt(zeroAmount, null));
        assertThrows(ValidationException.class, () -> validator.validateDebt(tooManyDecimals, null));
    }

    @Test
    void interestRateMustBeBetweenZeroAndHundred() {
        Debt debt = validDebt();
        debt.setInterestRate(new BigDecimal("101"));

        assertThrows(ValidationException.class, () -> validator.validateDebt(debt, null));
    }

    @Test
    void debtAmountCannotBeLessThanAlreadyPaid() {
        Debt debt = validDebt();

        assertThrows(ValidationException.class,
                () -> validator.validateDebt(debt, new BigDecimal("10000.01")));
    }

    @Test
    void validPaymentPasses() {
        assertDoesNotThrow(() -> validator.validatePayment(payment("500.00", TODAY), new BigDecimal("500.00"), TODAY));
    }

    @Test
    void paymentCannotExceedRemainingBalance() {
        assertThrows(ValidationException.class,
                () -> validator.validatePayment(payment("500.01", TODAY), new BigDecimal("500.00"), TODAY));
    }

    @Test
    void paymentMustBePositiveAndNotInFuture() {
        assertThrows(ValidationException.class,
                () -> validator.validatePayment(payment("-1", TODAY), new BigDecimal("500"), TODAY));
        assertThrows(ValidationException.class,
                () -> validator.validatePayment(payment("100", TODAY.plusDays(1)), new BigDecimal("500"), TODAY));
    }

    private static Debt validDebt() {
        Debt debt = new Debt();
        debt.setTitle("Кредит");
        debt.setCreditorName("Банк");
        debt.setOriginalAmount(new BigDecimal("10000.00"));
        debt.setStatus(DebtStatus.ACTIVE);
        return debt;
    }

    private static Payment payment(String amount, LocalDate date) {
        Payment payment = new Payment();
        payment.setDebtId(1L);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        return payment;
    }
}
