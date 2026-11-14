package kz.kvit.service;

import kz.kvit.i18n.I18n;
import kz.kvit.model.CalendarData;
import kz.kvit.model.DashboardData;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Installment;
import kz.kvit.model.Payment;
import kz.kvit.model.PaymentEntry;
import kz.kvit.model.ReportData;
import kz.kvit.repository.DebtRepository;
import kz.kvit.repository.InstallmentRepository;
import kz.kvit.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Application operations on debts, payments and payment schedules. Used by the JavaFX screens. */
public class DebtService {
    private static final int RECENT_PAYMENTS_LIMIT = 5;
    private static final int REPORT_MONTHS = 12;

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final InstallmentRepository installmentRepository;
    private final DebtCalculator calculator = new DebtCalculator();
    private final ReportCalculator reportCalculator = new ReportCalculator();
    private final DebtValidator validator = new DebtValidator();
    private final InstallmentPlanner installmentPlanner = new InstallmentPlanner();

    public DebtService(DebtRepository debtRepository, PaymentRepository paymentRepository,
                       InstallmentRepository installmentRepository) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.installmentRepository = installmentRepository;
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
        // Copies of loan payments made by the database are not shown twice in "latest payments".
        List<PaymentEntry> recent = toEntries(debts, payments).stream()
                .filter(entry -> !entry.payment().isMirror())
                .limit(RECENT_PAYMENTS_LIMIT)
                .toList();
        return new DashboardData(
                calculator.summarize(debts, payments, today),
                calculator.needingAttention(debts, payments, today),
                recent);
    }

    /** Reports cover my own debts in tenge, like the reports page of the web site. */
    public ReportData getReportData() {
        List<Debt> debts = debtRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        LocalDate today = LocalDate.now();
        var summary = calculator.summarize(debts, payments, today);
        List<Payment> owedPayments = calculator.paymentsOfDebts(calculator.owedInTenge(debts), payments);
        return new ReportData(
                reportCalculator.paymentsByMonth(owedPayments, YearMonth.from(today), REPORT_MONTHS),
                reportCalculator.byCreditor(debts, payments),
                reportCalculator.statuses(debts, payments, today),
                summary,
                reportCalculator.forecast(summary.totalRemaining(), owedPayments, today));
    }

    // ------------------------------------------------------------------ Debts

    /**
     * Creates or updates a debt. The user chooses only ACTIVE or CANCELLED;
     * PAID is set automatically when the payments cover the whole amount.
     */
    public Debt saveDebt(Debt debt) {
        List<Payment> payments = debt.getId() == null ? List.of() : paymentRepository.findByDebtId(debt.getId());
        if (debt.getId() != null) {
            keepLinkedFields(debt, debtRepository.findById(debt.getId()));
        }
        validator.validateDebt(debt, calculator.totalPaid(payments));
        debt.setStatus(calculator.statusAfterPayments(debt, payments));
        return debt.getId() == null ? debtRepository.insert(debt) : debtRepository.update(debt);
    }

    /**
     * A debt created from a friend loan or a split bill has a twin on the friend's side.
     * Its amount, currency, direction, creditor and status must stay equal to the twin,
     * so only the title, due date, rate and notes can be changed.
     */
    private void keepLinkedFields(Debt edited, Debt stored) {
        edited.setLoanId(stored.getLoanId());
        edited.setSplitId(stored.getSplitId());
        edited.setCounterpartyId(stored.getCounterpartyId());
        if (!stored.isLinked()) {
            return;
        }
        edited.setOriginalAmount(stored.getOriginalAmount());
        edited.setCurrency(stored.getCurrency());
        edited.setDirection(stored.getDirection());
        edited.setCreditorName(stored.getCreditorName());
        edited.setStatus(stored.getStatus());
    }

    public void deleteDebt(long debtId) {
        if (debtRepository.findById(debtId).isLinked()) {
            throw new ValidationException(I18n.t("debt.error.linkedDelete"));
        }
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
        if (payment.isMirror()) {
            throw new ValidationException(I18n.t("payment.error.mirrorDelete"));
        }
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

    // ------------------------------------------------------------------ Payment schedule

    public List<Installment> getSchedule(long debtId) {
        return installmentRepository.findByDebtId(debtId);
    }

    /** Preview for the form: amounts of the parts, without saving anything. */
    public List<BigDecimal> previewSchedule(BigDecimal remaining, int parts) {
        if (remaining.signum() <= 0 || parts < 1 || parts > InstallmentPlanner.MAX_PARTS) {
            return List.of();
        }
        return installmentPlanner.splitEqually(remaining, parts);
    }

    /** Replaces any existing schedule with a new one for the current remaining balance (as the web site does). */
    public List<Installment> createSchedule(long debtId, int parts, LocalDate firstDate) {
        Debt debt = debtRepository.findById(debtId);
        if (debt.getStatus() != DebtStatus.ACTIVE) {
            throw new ValidationException(I18n.t("schedule.error.closedDebt"));
        }
        BigDecimal remaining = calculator.remainingBalance(debt, paymentRepository.findByDebtId(debtId));
        List<Installment> schedule = installmentPlanner.buildSchedule(debtId, remaining, parts, firstDate, LocalDate.now());
        installmentRepository.deleteByDebtId(debtId);
        installmentRepository.insertAll(schedule);
        return installmentRepository.findByDebtId(debtId);
    }

    /**
     * Ticking an instalment records a real payment dated today and links it to the instalment.
     * The amount is capped at the remaining balance, in case other payments were made meanwhile.
     */
    public Payment payInstallment(Installment installment) {
        if (installment.isPaid()) {
            throw new ValidationException(I18n.t("schedule.error.alreadyPaid"));
        }
        Debt debt = debtRepository.findById(installment.debtId());
        BigDecimal remaining = calculator.remainingBalance(debt, paymentRepository.findByDebtId(debt.getId()));
        if (remaining.signum() <= 0) {
            throw new ValidationException(I18n.t("schedule.error.nothingLeft"));
        }
        Payment payment = new Payment();
        payment.setDebtId(debt.getId());
        payment.setAmount(installment.amount().min(remaining));
        payment.setPaymentDate(LocalDate.now());
        payment.setNotes(I18n.t("schedule.paymentNote", installment.seq()));
        Payment saved = addPayment(payment);
        installmentRepository.linkPayment(installment.id(), saved.getId());
        return saved;
    }

    public void deleteSchedule(long debtId) {
        installmentRepository.deleteByDebtId(debtId);
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
                    debt == null ? "—" : debt.getCreditorName(),
                    debt == null ? null : debt.getCurrency()));
        }
        return entries;
    }
}
