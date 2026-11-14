package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.model.Loan;
import kz.kvit.model.LoanStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the "loans" table and changes loan status the same way the web site does
 * (web/src/api/social.ts). RLS shows a loan only to its lender and borrower.
 */
public class LoanRepository {
    private static final String TABLE = "/rest/v1/loans";

    private final SupabaseClient client;

    public LoanRepository(SupabaseClient client) {
        this.client = client;
    }

    public List<Loan> findAll() {
        JsonNode rows = client.get(TABLE + "?select=*&order=created_at.desc");
        List<Loan> loans = new ArrayList<>();
        for (JsonNode row : rows) {
            loans.add(toLoan(row));
        }
        return loans;
    }

    /**
     * Confirms through the database function accept_loan(): it checks that the caller is the other
     * party, stores the card, creates the receipt number and the two linked debt records.
     * card is null unless the borrower has to provide it.
     */
    public void accept(String loanId, String card) {
        ObjectNode args = client.createObject();
        args.put("p_loan_id", loanId);
        args.put("p_card", card);
        client.rpc("accept_loan", args);
    }

    /** RLS allows only PENDING -> DECLINED or CANCELLED for a direct update. */
    public void setStatus(String loanId, LoanStatus status) {
        ObjectNode body = client.createObject().put("status", status.name());
        DebtRepository.singleRow(client.patch(TABLE + "?id=eq." + loanId, body));
    }

    private Loan toLoan(JsonNode row) {
        return new Loan(
                row.get("id").asText(),
                row.get("lender_id").asText(),
                row.get("borrower_id").asText(),
                row.get("initiator_id").asText(),
                row.get("amount").decimalValue(),
                row.path("purpose").asText(""),
                LocalDate.parse(row.get("due_date").asText()),
                DebtRepository.textOrNull(row, "borrower_card"),
                row.hasNonNull("interest_rate") ? row.get("interest_rate").decimalValue() : null,
                LoanStatus.valueOf(row.get("status").asText()),
                DebtRepository.textOrNull(row, "receipt_no"),
                OffsetDateTime.parse(row.get("created_at").asText()));
    }
}
