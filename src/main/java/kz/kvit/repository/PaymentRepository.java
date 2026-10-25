package kz.kvit.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.model.Payment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the "payments" table (filtered to the current user by Row Level Security). */
public class PaymentRepository {
    private static final String TABLE = "/rest/v1/payments";

    private final SupabaseClient client;

    public PaymentRepository(SupabaseClient client) {
        this.client = client;
    }

    public List<Payment> findAll() {
        return toPayments(client.get(TABLE + "?select=*&order=payment_date.desc,id.desc"));
    }

    public List<Payment> findByDebtId(long debtId) {
        return toPayments(client.get(TABLE + "?select=*&debt_id=eq." + debtId + "&order=payment_date.desc,id.desc"));
    }

    public Payment insert(Payment payment) {
        ObjectNode json = client.createObject();
        json.put("debt_id", payment.getDebtId());
        json.put("amount", payment.getAmount());
        json.put("payment_date", payment.getPaymentDate().toString());
        json.put("notes", DebtRepository.blankToNull(payment.getNotes()));
        return toPayment(DebtRepository.singleRow(client.post(TABLE, json)));
    }

    public void delete(long paymentId) {
        client.delete(TABLE + "?id=eq." + paymentId);
    }

    private List<Payment> toPayments(JsonNode rows) {
        List<Payment> payments = new ArrayList<>();
        for (JsonNode row : rows) {
            payments.add(toPayment(row));
        }
        return payments;
    }

    private Payment toPayment(JsonNode row) {
        Payment payment = new Payment();
        payment.setId(row.get("id").asLong());
        payment.setDebtId(row.get("debt_id").asLong());
        payment.setAmount(row.get("amount").decimalValue());
        payment.setPaymentDate(LocalDate.parse(row.get("payment_date").asText()));
        payment.setNotes(row.hasNonNull("notes") ? row.get("notes").asText() : null);
        return payment;
    }
}
