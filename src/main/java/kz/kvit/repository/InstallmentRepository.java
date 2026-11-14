package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.model.Installment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the "installments" table (payment schedule of a debt, see schema_web_2.sql). */
public class InstallmentRepository {
    private static final String TABLE = "/rest/v1/installments";

    private final SupabaseClient client;

    public InstallmentRepository(SupabaseClient client) {
        this.client = client;
    }

    public List<Installment> findByDebtId(long debtId) {
        JsonNode rows = client.get(TABLE + "?select=*&debt_id=eq." + debtId + "&order=seq.asc");
        List<Installment> result = new ArrayList<>();
        for (JsonNode row : rows) {
            result.add(toInstallment(row));
        }
        return result;
    }

    /** Inserts all rows in one request, so a schedule is never saved half-way. */
    public void insertAll(List<Installment> installments) {
        ArrayNode rows = client.createObject().arrayNode();
        for (Installment installment : installments) {
            ObjectNode row = rows.addObject();
            row.put("debt_id", installment.debtId());
            row.put("seq", installment.seq());
            row.put("due_date", installment.dueDate().toString());
            row.put("amount", installment.amount());
        }
        client.post(TABLE, rows);
    }

    public void deleteByDebtId(long debtId) {
        client.delete(TABLE + "?debt_id=eq." + debtId);
    }

    public void linkPayment(long installmentId, long paymentId) {
        ObjectNode body = client.createObject().put("payment_id", paymentId);
        DebtRepository.singleRow(client.patch(TABLE + "?id=eq." + installmentId, body));
    }

    private Installment toInstallment(JsonNode row) {
        return new Installment(
                row.get("id").asLong(),
                row.get("debt_id").asLong(),
                row.get("seq").asInt(),
                LocalDate.parse(row.get("due_date").asText()),
                row.get("amount").decimalValue(),
                row.hasNonNull("payment_id") ? row.get("payment_id").asLong() : null);
    }
}
