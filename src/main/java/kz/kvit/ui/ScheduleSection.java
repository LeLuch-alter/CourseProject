package kz.kvit.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.Installment;
import kz.kvit.service.DebtService;
import kz.kvit.service.InstallmentPlanner;
import kz.kvit.service.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payment schedule inside the debt details: split the remaining balance into N equal monthly
 * instalments. Marking one as paid records a real payment and links it (like Schedule.tsx on the web).
 */
public class ScheduleSection {
    private final DebtService debtService;
    private final ModalHost host;
    private final DebtOverview overview;
    private final Runnable onPaymentsChanged;
    private final InstallmentPlanner planner = new InstallmentPlanner();

    private final VBox body = new VBox(8);
    private final Button deleteButton = new Button(null, Icons.create(Icons.DELETE, 12));
    private final VBox root;

    /** @param onPaymentsChanged called after a payment was recorded, so the screen reloads balances */
    public ScheduleSection(DebtService debtService, ModalHost host, DebtOverview overview, Runnable onPaymentsChanged) {
        this.debtService = debtService;
        this.host = host;
        this.overview = overview;
        this.onPaymentsChanged = onPaymentsChanged;

        deleteButton.getStyleClass().addAll("quiet", "icon-button");
        Ui.tooltip(deleteButton, I18n.t("schedule.delete"));
        deleteButton.setOnAction(event -> confirmDelete());
        deleteButton.setVisible(false);

        HBox title = new HBox(8, Icons.create(Icons.CALENDAR, 13), Ui.label(I18n.t("schedule.title"), "ledger-title"),
                Ui.hSpacer(), deleteButton);
        title.setAlignment(Pos.CENTER_LEFT);
        root = new VBox(10, title, body);
        root.getStyleClass().add("planner");
        load();
    }

    public Node getRoot() {
        return root;
    }

    private void load() {
        body.getChildren().setAll(Ui.label(I18n.t("common.loading"), "helper"));
        BackgroundTask.run(() -> debtService.getSchedule(overview.debt().getId()),
                this::show,
                message -> {
                    deleteButton.setVisible(false);
                    body.getChildren().setAll(Ui.wrapped(message, "helper", "text-danger"));
                });
    }

    private void show(List<Installment> installments) {
        deleteButton.setVisible(!installments.isEmpty());
        if (installments.isEmpty()) {
            showCreateForm();
            return;
        }
        body.getChildren().clear();
        LocalDate today = LocalDate.now();
        int paidCount = 0;
        for (Installment installment : installments) {
            body.getChildren().add(row(installment, installments.size(), today));
            if (installment.isPaid()) {
                paidCount++;
            }
        }
        body.getChildren().add(Ui.label(I18n.t("schedule.progress", paidCount, installments.size()), "helper"));
    }

    private Node row(Installment installment, int total, LocalDate today) {
        String currency = overview.debt().getCurrency();
        Label date = Ui.label(Formats.date(installment.dueDate()) + "  ·  " + installment.seq() + "/" + total,
                installment.isPaid() ? "helper" : "detail-key");
        Label amount = Ui.label(Formats.money(installment.amount(), currency), "detail-value");
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        if (installment.isPaid()) {
            Label done = Icons.create(Icons.CHECK, 13);
            done.getStyleClass().add("text-success");
            Ui.tooltip(done, I18n.t("schedule.paid"));
            row.getChildren().addAll(done, date, Ui.hSpacer(), amount);
            return row;
        }
        Button pay = new Button(null, Icons.create(Icons.CHECK, 12));
        pay.getStyleClass().add("icon-button");
        Ui.tooltip(pay, I18n.t("schedule.markPaid"));
        pay.setOnAction(event -> confirmPay(installment, pay));
        row.getChildren().addAll(pay, date);
        if (installment.isLate(today)) {
            row.getChildren().add(Ui.statusChip(I18n.t("status.OVERDUE"), "danger"));
        }
        row.getChildren().addAll(Ui.hSpacer(), amount);
        return row;
    }

    private void showCreateForm() {
        TextField partsField = new TextField("3");
        partsField.setPrefColumnCount(4);
        DatePicker firstDate = new DatePicker(LocalDate.now().plusMonths(1));
        firstDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        Label preview = Ui.wrapped("", "helper");
        Runnable updatePreview = () -> preview.setText(previewText(partsField.getText()));
        partsField.textProperty().addListener((obs, oldText, text) -> updatePreview.run());
        updatePreview.run();

        Button create = new Button(I18n.t("schedule.create"));
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> create(partsField.getText(), firstDate, create));

        VBox partsBox = Ui.field(I18n.t("schedule.parts"), partsField);
        VBox dateBox = Ui.field(I18n.t("schedule.firstDate"), firstDate);
        HBox fields = new HBox(10, partsBox, dateBox);
        HBox.setHgrow(dateBox, Priority.ALWAYS);
        body.getChildren().setAll(Ui.wrapped(I18n.t("schedule.hint"), "helper"), fields, preview, create);
    }

    private String previewText(String partsText) {
        try {
            int parts = planner.parseParts(partsText);
            List<BigDecimal> amounts = debtService.previewSchedule(overview.remaining(), parts);
            if (amounts.isEmpty()) {
                return I18n.t("schedule.error.parts", InstallmentPlanner.MAX_PARTS);
            }
            return I18n.t("schedule.preview", amounts.size(), Formats.money(amounts.get(0), overview.debt().getCurrency()));
        } catch (ValidationException e) {
            return e.getMessage();
        }
    }

    private void create(String partsText, DatePicker firstDate, Button button) {
        int parts;
        LocalDate first;
        try {
            parts = planner.parseParts(partsText);
            first = DebtForm.readDate(firstDate);
        } catch (ValidationException e) {
            host.toast(e.getMessage(), true);
            return;
        }
        button.setDisable(true);
        BackgroundTask.run(() -> debtService.createSchedule(overview.debt().getId(), parts, first),
                created -> {
                    host.toast(I18n.t("schedule.created"), false);
                    show(created);
                },
                message -> {
                    button.setDisable(false);
                    host.toast(message, true);
                });
    }

    private void confirmPay(Installment installment, Button button) {
        BigDecimal amount = installment.amount().min(overview.remaining());
        host.confirm(I18n.t("schedule.pay.title"),
                I18n.t("schedule.pay.text", installment.seq(), Formats.money(amount, overview.debt().getCurrency()),
                        Formats.date(LocalDate.now())),
                I18n.t("schedule.markPaid"), false,
                () -> {
                    button.setDisable(true);
                    BackgroundTask.run(() -> debtService.payInstallment(installment),
                            saved -> {
                                host.toast(I18n.t("toast.paymentAdded",
                                        Formats.money(saved.getAmount(), overview.debt().getCurrency())), false);
                                onPaymentsChanged.run();
                            },
                            message -> {
                                button.setDisable(false);
                                host.toast(message, true);
                            });
                });
    }

    private void confirmDelete() {
        host.confirm(I18n.t("schedule.delete.title"), I18n.t("schedule.delete.text"), I18n.t("common.delete"), true,
                () -> BackgroundTask.run(() -> {
                            debtService.deleteSchedule(overview.debt().getId());
                            return null;
                        },
                        result -> {
                            host.toast(I18n.t("schedule.deleted"), false);
                            load();
                        },
                        message -> host.toast(message, true)));
    }
}
