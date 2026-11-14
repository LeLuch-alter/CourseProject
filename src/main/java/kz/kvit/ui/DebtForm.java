package kz.kvit.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import kz.kvit.i18n.I18n;
import kz.kvit.model.Currencies;
import kz.kvit.model.Debt;
import kz.kvit.model.DebtDirection;
import kz.kvit.model.DebtStatus;
import kz.kvit.service.DebtService;
import kz.kvit.service.DebtValidator;
import kz.kvit.service.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

/** Dialog for creating or editing a debt. */
public class DebtForm {
    private final DebtService debtService;
    private final ModalHost host;
    private final Debt existing;
    private final Consumer<Debt> onSaved;
    private final DebtValidator validator = new DebtValidator();

    private final TextField titleField = new TextField();
    private final TextField creditorField = new TextField();
    private final TextField amountField = new TextField();
    private final TextField rateField = new TextField();
    private final DatePicker dueDatePicker = new DatePicker();
    private final ComboBox<DebtStatus> statusBox = new ComboBox<>();
    private final ComboBox<DebtDirection> directionBox = new ComboBox<>();
    private final ComboBox<String> currencyBox = new ComboBox<>();
    private final Label creditorCaption = Ui.label("", "field-label");
    private final TextArea notesArea = new TextArea();
    private final InfoBar infoBar = new InfoBar();
    private final Button saveButton = new Button();

    /** @param existing the debt to edit, or null to create a new one */
    public static void open(ModalHost host, DebtService debtService, Debt existing, Consumer<Debt> onSaved) {
        new DebtForm(host, debtService, existing, onSaved).show();
    }

    private DebtForm(ModalHost host, DebtService debtService, Debt existing, Consumer<Debt> onSaved) {
        this.host = host;
        this.debtService = debtService;
        this.existing = existing;
        this.onSaved = onSaved;
    }

    private void show() {
        titleField.setPromptText(I18n.t("form.title.prompt"));
        creditorField.setPromptText(I18n.t("form.creditor.prompt"));
        amountField.setPromptText("150 000");
        rateField.setPromptText(I18n.t("form.optional"));
        dueDatePicker.setPromptText(I18n.t("form.optional"));
        notesArea.setPromptText(I18n.t("form.optional"));
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        // The user chooses only ACTIVE or CANCELLED; PAID follows from the payments.
        statusBox.setItems(FXCollections.observableArrayList(DebtStatus.ACTIVE, DebtStatus.CANCELLED));
        statusBox.setValue(DebtStatus.ACTIVE);

        directionBox.setItems(FXCollections.observableArrayList(DebtDirection.values()));
        directionBox.setValue(DebtDirection.OWED);
        directionBox.valueProperty().addListener((obs, oldValue, value) -> updateCreditorCaption());
        currencyBox.setItems(FXCollections.observableArrayList(Currencies.ALL));
        currencyBox.setValue(Currencies.TENGE);
        currencyBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null ? "" : Currencies.symbol(code) + "  " + code;
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });

        if (existing != null) {
            directionBox.setValue(existing.getDirection());
            currencyBox.setValue(existing.getCurrency());
            titleField.setText(existing.getTitle());
            creditorField.setText(existing.getCreditorName());
            amountField.setText(existing.getOriginalAmount().toPlainString());
            rateField.setText(existing.getInterestRate() == null ? "" : existing.getInterestRate().toPlainString());
            dueDatePicker.setValue(existing.getDueDate());
            statusBox.setValue(existing.getStatus() == DebtStatus.CANCELLED ? DebtStatus.CANCELLED : DebtStatus.ACTIVE);
            notesArea.setText(existing.getNotes());
        }

        // Fields shared with the friend's twin record cannot be changed here (same as on the web site).
        boolean linked = existing != null && existing.isLinked();
        creditorField.setDisable(linked);
        amountField.setDisable(linked);
        currencyBox.setDisable(linked);
        directionBox.setDisable(linked);
        statusBox.setDisable(linked);
        updateCreditorCaption();

        currencyBox.setPrefWidth(110);
        currencyBox.setMinWidth(110);
        HBox amountRow = new HBox(8, amountField, currencyBox);
        HBox.setHgrow(amountField, Priority.ALWAYS);
        VBox creditorBox = new VBox(6, creditorCaption, creditorField);
        creditorField.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.add(Ui.field(I18n.t("form.title"), titleField), 0, 0, 2, 1);
        grid.add(Ui.field(I18n.t("debt.direction"), directionBox), 0, 1);
        grid.add(creditorBox, 1, 1);
        grid.add(Ui.field(I18n.t("form.amount"), amountRow), 0, 2);
        grid.add(Ui.field(I18n.t("form.rate"), rateField), 1, 2);
        grid.add(Ui.field(I18n.t("form.dueDate"), dueDatePicker), 0, 3);
        grid.add(Ui.field(I18n.t("form.status"), statusBox), 1, 3);
        grid.add(Ui.field(I18n.t("form.notes"), notesArea), 0, 4, 2, 1);
        for (int column = 0; column < 2; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(50);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }

        Button cancel = new Button(I18n.t("common.cancel"));
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> host.closeModal());
        saveButton.setText(I18n.t(existing == null ? "form.debt.create" : "common.save"));
        saveButton.getStyleClass().add("cta");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> save());

        VBox content = new VBox(16, grid, Ui.wrapped(I18n.t("form.rate.hint"), "helper"));
        if (linked) {
            content.getChildren().add(Ui.wrapped(I18n.t("debt.linked.text"), "helper"));
        }
        content.getChildren().add(infoBar);
        VBox card = ModalHost.dialogCard(I18n.t(existing == null ? "form.debt.newTitle" : "form.debt.editTitle"),
                content, cancel, saveButton);
        card.setPrefWidth(540);
        host.showDialog(card, null);
        Platform.runLater(titleField::requestFocus);
    }

    private void save() {
        Debt debt;
        try {
            debt = readForm();
        } catch (ValidationException e) {
            infoBar.show(InfoBar.Severity.ERROR, e.getMessage());
            return;
        }
        saveButton.setDisable(true);
        BackgroundTask.run(
                () -> debtService.saveDebt(debt),
                saved -> {
                    host.closeModal();
                    host.toast(I18n.t(existing == null ? "toast.debtCreated" : "toast.debtSaved"), false);
                    onSaved.accept(saved);
                },
                message -> {
                    saveButton.setDisable(false);
                    infoBar.show(InfoBar.Severity.ERROR, message);
                });
    }

    private Debt readForm() {
        Debt debt = new Debt();
        if (existing != null) {
            debt.setId(existing.getId());
            debt.setCreatedAt(existing.getCreatedAt());
        }
        debt.setTitle(titleField.getText());
        debt.setCreditorName(creditorField.getText());
        debt.setOriginalAmount(validator.parseAmount(amountField.getText(), I18n.t("form.amount"), true));
        debt.setInterestRate(validator.parseAmount(rateField.getText(), I18n.t("form.rate"), false));
        debt.setDueDate(readDate(dueDatePicker));
        debt.setStatus(statusBox.getValue());
        debt.setNotes(notesArea.getText());
        debt.setDirection(directionBox.getValue());
        debt.setCurrency(currencyBox.getValue());
        // Checked here as well, so the user sees the problem without waiting for the server.
        validator.validateDebt(debt, null);
        return debt;
    }

    /** "Creditor" when I owe, "Debtor" when the money is owed to me. */
    private void updateCreditorCaption() {
        boolean lent = directionBox.getValue() == DebtDirection.LENT;
        creditorCaption.setText(I18n.t(lent ? "debt.debtor" : "form.creditor"));
        creditorField.setPromptText(I18n.t(lent ? "debt.debtor.prompt" : "form.creditor.prompt"));
    }

    /** A date typed by hand is only applied after Enter; this reads it on save as well. */
    static LocalDate readDate(DatePicker picker) {
        String typed = picker.getEditor().getText();
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return picker.getConverter().fromString(typed);
        } catch (DateTimeParseException e) {
            throw new ValidationException(I18n.t("error.date", typed));
        }
    }
}
