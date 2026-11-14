package kz.kvit.model;

/**
 * A loan as seen by the current user.
 *
 * @param otherName      display name of the other party
 * @param iAmLender      true if the current user gives the money
 * @param waitingForMe   pending, and the current user is the one who has to confirm or decline
 * @param cardRequired   confirming needs the current user's card number (borrower, no card stored yet)
 */
public record LoanEntry(Loan loan, String otherName, boolean iAmLender, boolean waitingForMe, boolean cardRequired) {
}
