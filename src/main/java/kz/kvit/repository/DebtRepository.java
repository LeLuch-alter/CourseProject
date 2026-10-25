package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.i18n.I18n;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the "debts" table. Row Level Security on the server makes sure
 * only the current user's rows are returned, so no user filter is needed here.
 */
public class DebtRepository {
    private static final String TABLE = "/rest/v1/debts";

    private final SupabaseClient client;

    public DebtRepository(SupabaseClient client) {
        this.client = client;
    }

    public List<Debt> findAll() {
        JsonNode rows = client.get(TABLE + "?select=*&order=created_at.desc");
        List<Debt> debts = new ArrayList<>();
        for (JsonNode row : rows) {
            debts.add(toDebt(row));
        }
        return debts;
    }

    public Debt findById(long debtId) {
        return toDebt(singleRow(client.get(TABLE + "?select=*&id=eq." + debtId)));
    }

    public Debt insert(Debt debt) {
        JsonNode rows = client.post(TABLE, toJson(debt));
        return toDebt(singleRow(rows));
    }

    public Debt update(Debt debt) {
        JsonNode rows = client.patch(TABLE + "?id=eq." + debt.getId(), toJson(debt));
        return toDebt(singleRow(rows));
    }

    public void updateStatus(long debtId, DebtStatus status) {
        ObjectNode body = client.createObject().put("status", status.name());
        singleRow(client.patch(TABLE + "?id=eq." + debtId, body));
    }

    /** Payments of the debt are deleted by the database (ON DELETE CASCADE). */
    public void delete(long debtId) {
        client.delete(TABLE + "?id=eq." + debtId);
    }

    // id, user_id and created_at are not sent: the database fills them in itself.
    private ObjectNode toJson(Debt debt) {
        ObjectNode json = client.createObject();
        json.put("title", debt.getTitle().trim());
        json.put("creditor_name", debt.getCreditorName().trim());
        json.put("original_amount", debt.getOriginalAmount());
        json.put("interest_rate", debt.getInterestRate());
        json.put("due_date", debt.getDueDate() == null ? null : debt.getDueDate().toString());
        json.put("status", debt.getStatus().name());
        json.put("notes", blankToNull(debt.getNotes()));
        return json;
    }

    private Debt toDebt(JsonNode row) {
        Debt debt = new Debt();
        debt.setId(row.get("id").asLong());
        debt.setTitle(row.get("title").asText());
        debt.setCreditorName(row.get("creditor_name").asText());
        debt.setOriginalAmount(row.get("original_amount").decimalValue());
        debt.setInterestRate(row.hasNonNull("interest_rate") ? row.get("interest_rate").decimalValue() : null);
        debt.setCreatedAt(OffsetDateTime.parse(row.get("created_at").asText()));
        debt.setDueDate(row.hasNonNull("due_date") ? LocalDate.parse(row.get("due_date").asText()) : null);
        debt.setStatus(DebtStatus.valueOf(row.get("status").asText()));
        debt.setNotes(row.hasNonNull("notes") ? row.get("notes").asText() : null);
        return debt;
    }

    static JsonNode singleRow(JsonNode rows) {
        if (!rows.isArray() || rows.isEmpty()) {
            throw new DataAccessException(I18n.t("error.db.notFound"));
        }
        return rows.get(0);
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
