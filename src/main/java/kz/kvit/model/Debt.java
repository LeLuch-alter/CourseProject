package kz.kvit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class Debt {
    private Long id;
    private String title;
    private String creditorName;
    private BigDecimal originalAmount;
    private BigDecimal interestRate;
    private OffsetDateTime createdAt;
    private LocalDate dueDate;
    private DebtStatus status = DebtStatus.ACTIVE;
    private String notes;
    private String userId;
    private DebtDirection direction = DebtDirection.OWED;
    private String currency = Currencies.TENGE;
    // Set by the database when the debt was created from a friend loan or a split bill.
    private String loanId;
    private String splitId;
    private String counterpartyId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public DebtStatus getStatus() {
        return status;
    }

    public void setStatus(DebtStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public DebtDirection getDirection() {
        return direction;
    }

    public void setDirection(DebtDirection direction) {
        this.direction = direction;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getSplitId() {
        return splitId;
    }

    public void setSplitId(String splitId) {
        this.splitId = splitId;
    }

    public String getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(String counterpartyId) {
        this.counterpartyId = counterpartyId;
    }

    public boolean isLent() {
        return direction == DebtDirection.LENT;
    }

    public boolean isInTenge() {
        return currency == null || Currencies.TENGE.equals(currency);
    }

    /**
     * A debt created from a friend loan or a split bill has a twin record on the other person's side.
     * Its amount, currency, direction, creditor and status are managed together with that twin,
     * so they are read-only here (the web site behaves the same way).
     */
    public boolean isLinked() {
        return loanId != null || splitId != null;
    }
}
