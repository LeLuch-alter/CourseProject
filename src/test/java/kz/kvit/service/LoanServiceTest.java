package kz.kvit.service;

import kz.kvit.model.Loan;
import kz.kvit.model.LoanEntry;
import kz.kvit.model.LoanStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanServiceTest {
    private static final String ME = "me";
    private static final String FRIEND = "friend";

    @Test
    void theOtherPartyMustAnswerAPendingRequest() {
        // The friend asked me (the lender) for money: I have to answer, no card needed from me.
        LoanEntry askedMe = LoanService.toEntry(loan(ME, FRIEND, FRIEND, LoanStatus.PENDING, "4400000000000000"), ME, "Aigerim");
        assertTrue(askedMe.iAmLender());
        assertTrue(askedMe.waitingForMe());
        assertFalse(askedMe.cardRequired());

        // I created the request myself: the friend has to answer.
        LoanEntry mine = LoanService.toEntry(loan(ME, FRIEND, ME, LoanStatus.PENDING, null), ME, "Aigerim");
        assertFalse(mine.waitingForMe());
    }

    @Test
    void borrowerWithoutStoredCardMustEnterIt() {
        LoanEntry offeredToMe = LoanService.toEntry(loan(FRIEND, ME, FRIEND, LoanStatus.PENDING, null), ME, "Aigerim");

        assertFalse(offeredToMe.iAmLender());
        assertTrue(offeredToMe.waitingForMe());
        assertTrue(offeredToMe.cardRequired());
    }

    @Test
    void activeLoansNeedNoAnswer() {
        LoanEntry active = LoanService.toEntry(loan(FRIEND, ME, FRIEND, LoanStatus.ACTIVE, null), ME, "Aigerim");

        assertFalse(active.waitingForMe());
        assertFalse(active.cardRequired());
    }

    @Test
    void cardMustBeSixteenDigits() {
        LoanService service = new LoanService(null, null, null);

        assertEquals("4400430012345678", service.normalizeCard("4400 4300 1234 5678"));
        assertThrows(ValidationException.class, () -> service.normalizeCard("4400 4300 1234"));
        assertThrows(ValidationException.class, () -> service.normalizeCard("4400-4300-1234-567X"));
        assertThrows(ValidationException.class, () -> service.normalizeCard(null));
    }

    private static Loan loan(String lender, String borrower, String initiator, LoanStatus status, String card) {
        return new Loan("id-1", lender, borrower, initiator, new BigDecimal("50000.00"), "Ремонт",
                LocalDate.of(2026, 12, 1), card, null, status, null, null);
    }
}
