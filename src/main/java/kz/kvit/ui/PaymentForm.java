package kz.kvit.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;
import kz.kvit.service.DebtService;
import kz.kvit.service.DebtValidator;
import kz.kvit.service.ValidationException;

import java.time.LocalDate;
import java.util.List;

/** Dialog for registering a payment against one of the open debts. */
public class PaymentForm {
    private final ModalHost host;
    private final DebtService debtService;
    private final Runnable onSaved;
    private final DebtValidator validator = new DebtValidator();

    private final ComboBox<DebtOverview> debtBox = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TextArea notesArea = new TextArea();
    private final Label remainingLabel = Ui.label("", "helper");
    private final InfoBar infoBar = new InfoBar();
    private final Button saveButton = new Button(I18n.t("form.payment.save"));

    /**
     * Loads the open debts and shows the dialog.
     * @param preselectedDebtId debt to select at start, or null
     */
    public static void open(ModalHost host, DebtService debtService, Long preselectedDebtId, Runnable onSaved) {
        BackgroundTask.run(debtService::getDebtOverviews,
                debts -> {
                    List<DebtOverview> open = debts.stream()
                            .filter(overview -> overview.debt().getStatus() == DebtStatus.ACTIVE)
                            .filter(overview -> overview.remaining().signum() > 0)
                            .toList();
                    if (open.isEmpty()) {
                        host.toast(I18n.t("form.payment.noOpenDebts"), true);
                        return;
                    }
                    new PaymentForm(host, debtService, onSaved).show(open, preselectedDebtId);
                },
                message -> host.toast(message, true));
    }

    private PaymentForm(ModalHost host, DebtService debtService, Runnable onSaved) {
        this.host = host;
        this.debtService = debtService;
        this.onSaved = onSaved;
    }

    private void show(List<DebtOverview> debts, Long preselectedDebtId) {
        debtBox.setItems(FXCollections.observableArrayList(debts));
        debtBox.setCellFactory(list -> new DebtCell());
        debtBox.setButtonCell(new DebtCell());
        debtBox.valueProperty().addListener((obs, oldDebt, debt) -> showRemaining(debt));
        debtBox.setValue(debts.stream()
                .filter(overview -> overview.debt().getId().equals(preselectedDebtId))
                .findFirst().orElse(debts.get(0)));

        amountField.setPromptText("10 000");
        Button wholeRemaining = new Button(I18n.t("form.payment.all"));
        wholeRemaining.getStyleClass().add("quiet");
        wholeRemaining.setOnAction(event -> amountField.setText(debtBox.getValue().remaining().toPlainString()));
        HBox amountRow = new HBox(8, amountField, wholeRemaining);
        HBox.setHgrow(amountField, Priority.ALWAYS);

        // Payments cannot be dated in the future: those days are disabled in the calendar.
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        notesArea.setPromptText(I18n.t("form.optional"));
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        Button cancel = new Button(I18n.t("common.cancel"));
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> host.closeModal());
        saveButton.getStyleClass().add("cta");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> save());

        VBox content = new VBox(14,
                Ui.field(I18n.t("form.payment.debt"), debtBox),
                remainingLabel,
                Ui.field(I18n.t("form.paymentAmount"), amountRow),
                Ui.field(I18n.t("form.payment.date"), datePicker),
                Ui.field(I18n.t("form.notes"), notesArea),
                infoBar);
        VBox card = ModalHost.dialogCard(I18n.t("form.payment.title"), content, cancel, saveButton);
        host.showDialog(card, null);
        Platform.runLater(amountField::requestFocus);
    }

    private void showRemaining(DebtOverview debt) {
        remainingLabel.setText(debt == null ? "" : I18n.t("form.payment.remaining", Formats.money(debt.remaining())));
    }

    private void save() {
        Payment payment = new Payment();
        try {
            payment.setDebtId(debtBox.getValue() == null ? null : debtBox.getValue().debt().getId());
            payment.setAmount(validator.parseAmount(amountField.getText(), I18n.t("form.paymentAmount"), true));
            payment.setPaymentDate(DebtForm.readDate(datePicker));
            payment.setNotes(notesArea.getText());
            validator.validatePayment(payment, debtBox.getValue().remaining(), LocalDate.now());
        } catch (ValidationException e) {
            infoBar.show(InfoBar.Severity.ERROR, e.getMessage());
            return;
        }

        saveButton.setDisable(true);
        BackgroundTask.run(
                () -> debtService.addPayment(payment),
                saved -> {
                    host.closeModal();
                    host.toast(I18n.t("toast.paymentAdded", Formats.money(saved.getAmount())), false);
                    onSaved.run();
                },
                message -> {
                    saveButton.setDisable(false);
                    infoBar.show(InfoBar.Severity.ERROR, message);
                });
    }

    /** "Кредит на ноутбук · Kaspi Bank" in the drop-down list. */
    private static class DebtCell extends ListCell<DebtOverview> {
        @Override
        protected void updateItem(DebtOverview item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null
                    : item.debt().getTitle() + "  ·  " + item.debt().getCreditorName());
        }
    }
}
