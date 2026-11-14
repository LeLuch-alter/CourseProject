package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.Loan;
import kz.kvit.model.LoanEntry;
import kz.kvit.model.LoanStatus;
import kz.kvit.repository.LoanRepository;
import kz.kvit.repository.ProfileRepository;
import kz.kvit.repository.SupabaseClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loans between friends ("расписки"). They are created on the web site; the desktop app lists them
 * and lets the other party confirm or decline a pending request, or the initiator withdraw it.
 * All checks that really matter (who may confirm, card number) are repeated by the database.
 */
public class LoanService {
    private final LoanRepository loanRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseClient client;

    public LoanService(LoanRepository loanRepository, ProfileRepository profileRepository, SupabaseClient client) {
        this.loanRepository = loanRepository;
        this.profileRepository = profileRepository;
        this.client = client;
    }

    public List<LoanEntry> getLoans() {
        String me = client.getCurrentUserId();
        List<Loan> loans = loanRepository.findAll();
        Set<String> otherIds = new TreeSet<>();
        for (Loan loan : loans) {
            otherIds.add(otherParty(loan, me));
        }
        Map<String, String> names = profileRepository.findDisplayNames(otherIds);

        List<LoanEntry> entries = new ArrayList<>();
        for (Loan loan : loans) {
            String name = names.getOrDefault(otherParty(loan, me), I18n.t("loans.unknownUser"));
            entries.add(toEntry(loan, me, name));
        }
        return entries;
    }

    /** card is only used when the current user is the borrower and no card is stored yet. */
    public void accept(LoanEntry entry, String card) {
        if (!entry.waitingForMe()) {
            throw new ValidationException(I18n.t("loans.error.notYours"));
        }
        String cleanCard = null;
        if (entry.cardRequired()) {
            cleanCard = normalizeCard(card);
        }
        loanRepository.accept(entry.loan().id(), cleanCard);
    }

    public void decline(LoanEntry entry) {
        if (!entry.waitingForMe()) {
            throw new ValidationException(I18n.t("loans.error.notYours"));
        }
        loanRepository.setStatus(entry.loan().id(), LoanStatus.DECLINED);
    }

    /** The initiator takes back a request that the friend has not answered yet. */
    public void withdraw(LoanEntry entry) {
        if (entry.loan().status() != LoanStatus.PENDING || entry.waitingForMe()) {
            throw new ValidationException(I18n.t("loans.error.notYours"));
        }
        loanRepository.setStatus(entry.loan().id(), LoanStatus.CANCELLED);
    }

    static LoanEntry toEntry(Loan loan, String me, String otherName) {
        boolean iAmLender = loan.lenderId().equals(me);
        boolean waitingForMe = loan.status() == LoanStatus.PENDING && !loan.initiatorId().equals(me);
        boolean cardRequired = waitingForMe && !iAmLender && loan.borrowerCard() == null;
        return new LoanEntry(loan, otherName, iAmLender, waitingForMe, cardRequired);
    }

    /**
     * Only the 16 digits of the card number are stored (no expiry date, no CVV),
     * the same rule as the database check on loans.borrower_card. Spaces are allowed while typing.
     */
    String normalizeCard(String card) {
        String digits = card == null ? "" : card.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{16}")) {
            throw new ValidationException(I18n.t("loans.error.card"));
        }
        return digits;
    }

    private static String otherParty(Loan loan, String me) {
        return loan.lenderId().equals(me) ? loan.borrowerId() : loan.lenderId();
    }
}
