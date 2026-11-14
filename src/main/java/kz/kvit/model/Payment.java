package kz.kvit.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Payment {
    private Long id;
    private Long debtId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String notes;
    // Id of the original payment when this row is the database's copy for the other side of a loan.
    private Long mirrorOf;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDebtId() {
        return debtId;
    }

    public void setDebtId(Long debtId) {
        this.debtId = debtId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getMirrorOf() {
        return mirrorOf;
    }

    public void setMirrorOf(Long mirrorOf) {
        this.mirrorOf = mirrorOf;
    }

    /** Copies are created and deleted by the database together with the original payment. */
    public boolean isMirror() {
        return mirrorOf != null;
    }
}
