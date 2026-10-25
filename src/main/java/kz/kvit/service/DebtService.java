package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.CalendarData;
import kz.kvit.model.DashboardData;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;
import kz.kvit.model.PaymentEntry;
import kz.kvit.model.ReportData;
import kz.kvit.repository.DebtRepository;
import kz.kvit.repository.PaymentRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Application operations on debts and payments. Used by the JavaFX screens. */
public class DebtService {
    private static final int RECENT_PAYMENTS_LIMIT = 5;
    private static final int REPORT_MONTHS = 12;

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final DebtCalculator calculator = new DebtCalculator();
    private final ReportCalculator reportCalculator = new ReportCalculator();
    private final DebtValidator validator = new DebtValidator();

    public DebtService(DebtRepository debtRepository, PaymentRepository paymentRepository) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
    }

    // ------------------------------------------------------------------ Reading

    public List<DebtOverview> getDebtOverviews() {
        List<Debt> debts = debtRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        LocalDate today = LocalDate.now();
        List<DebtOverview> result = new ArrayList<>();
        for (Debt debt : debts) {
            result.add(calculator.overview(debt, calculator.paymentsOf(debt, payments), today));
        }
        return result;
    }

    public List<Payment> getPaymentsOfDebt(long debtId) {
        return paymentRepository.findByDebtId(debtId);
    }

    /** All payments, newest first, with the title and creditor of their debt. */
    public List<PaymentEntry> getPaymentEntries() {
        return toEntries(debtRepository.findAll(), paymentRepository.findAll());
    }

    public CalendarData getCalendarData() {
        List<Debt> debts = debtRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        LocalDate today = LocalDate.now();
        List<DebtOverview> overviews = new ArrayList<>();
        for (Debt debt : debts) {
            overviews.add(calculator.overview(debt, calculator.paymentsOf(debt, payments), today));
        }
        return new CalendarData(overviews, toEntries(debts, payments));
    }

    public DashboardData getDashboardData() {
        List<Debt> debts = debtRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        LocalDate today = LocalDate.now();
        List<PaymentEntry> recent = toEntries(debts, payments).stream().limit(RECENT_PAYMENTS_LIMIT).toList();
        return new DashboardData(
                calculator.summarize(debts, payments, today),
                calculator.needingAttention(debts, payments, today),
                recent);
    }

    public ReportData getReportData() {
        List<Debt> debts = debtRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        LocalDate today = LocalDate.now();
        var summary = calculator.summarize(debts, payments, today);
        return new ReportData(
                reportCalculator.paymentsByMonth(payments, YearMonth.from(today), REPORT_MONTHS),
                reportCalculator.byCreditor(debts, payments),
                reportCalculator.statuses(debts, payments, today),
                summary,
                reportCalculator.forecast(summary.totalRemaining(), payments, today));
    }

    // ------------------------------------------------------------------ Debts

    /**
     * Creates or updates a debt. The user chooses only ACTIVE or CANCELLED;
     * PAID is set automatically when the payments cover the whole amount.
     */
    public Debt saveDebt(Debt debt) {
        List<Payment> payments = debt.getId() == null ? List.of() : paymentRepository.findByDebtId(debt.getId());
        validator.validateDebt(debt, calculator.totalPaid(payments));
        debt.setStatus(calculator.statusAfterPayments(debt, payments));
        return debt.getId() == null ? debtRepository.insert(debt) : debtRepository.update(debt);
    }

    public void deleteDebt(long debtId) {
        debtRepository.delete(debtId);
    }

    // ------------------------------------------------------------------ Payments

    public Payment addPayment(Payment payment) {
        if (payment.getDebtId() == null) {
            throw new ValidationException(I18n.t("error.payment.noDebt"));
        }
        Debt debt = debtRepository.findById(payment.getDebtId());
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            throw new ValidationException(I18n.t("error.payment.cancelledDebt"));
        }
        List<Payment> payments = new ArrayList<>(paymentRepository.findByDebtId(debt.getId()));
        validator.validatePayment(payment, calculator.remainingBalance(debt, payments), LocalDate.now());

        Payment saved = paymentRepository.insert(payment);
        payments.add(saved);
        updateStatusIfChanged(debt, payments);
        return saved;
    }

    public void deletePayment(Payment payment) {
        paymentRepository.delete(payment.getId());
        Debt debt = debtRepository.findById(payment.getDebtId());
        updateStatusIfChanged(debt, paymentRepository.findByDebtId(debt.getId()));
    }

    private void updateStatusIfChanged(Debt debt, List<Payment> payments) {
        DebtStatus newStatus = calculator.statusAfterPayments(debt, payments);
        if (newStatus != debt.getStatus()) {
            debtRepository.updateStatus(debt.getId(), newStatus);
        }
    }

    private List<PaymentEntry> toEntries(List<Debt> debts, List<Payment> payments) {
        Map<Long, Debt> debtsById = new HashMap<>();
        for (Debt debt : debts) {
            debtsById.put(debt.getId(), debt);
        }
        List<PaymentEntry> entries = new ArrayList<>();
        for (Payment payment : payments) {
            Debt debt = debtsById.get(payment.getDebtId());
            entries.add(new PaymentEntry(payment,
                    debt == null ? "—" : debt.getTitle(),
                    debt == null ? "—" : debt.getCreditorName()));
        }
        return entries;
    }
}
