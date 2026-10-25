package kz.kvit.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import kz.kvit.i18n.I18n;
import kz.kvit.model.PaymentEntry;
import kz.kvit.service.CsvExporter;
import kz.kvit.service.DebtService;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** All payments with a period filter, search, totals for the period and CSV export. */
public class PaymentsView implements Page {

    private enum Period { ALL, MONTH, QUARTER, YEAR }

    private final DebtService debtService;
    private final ModalHost host;

    private final ObservableList<PaymentEntry> payments = FXCollections.observableArrayList();
    private final FilteredList<PaymentEntry> filtered = new FilteredList<>(payments);
    private final TableView<PaymentEntry> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final StackPane tableArea = new StackPane();
    private final Label totalValue = Ui.label("—", "stat-value");
    private final Label countValue = Ui.label("—", "stat-value");
    private final Label averageValue = Ui.label("—", "stat-value");
    private final VBox root;
    private Period period = Period.ALL;

    public PaymentsView(DebtService debtService, ModalHost host) {
        this.debtService = debtService;
        this.host = host;

        Button export = new Button(I18n.t("common.exportCsv"), Icons.create(Icons.EXPORT, 13));
        export.setOnAction(event -> exportCsv());
        Button add = new Button(I18n.t("payments.new"), Icons.create(Icons.ADD, 13));
        add.setOnAction(event -> createNew());
        Ui.tooltip(add, "Ctrl+N");

        root = Ui.page(I18n.t("payments.eyebrow"), I18n.t("payments.title"), new HBox(8, export, add));
        Node panel = buildPanel();
        VBox.setVgrow(panel, Priority.ALWAYS);
        root.getChildren().addAll(buildSummary(), panel);
        load();
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public void refresh() {
        load();
    }

    @Override
    public void createNew() {
        PaymentForm.open(host, debtService, null, this::load);
    }

    @Override
    public void focusSearch() {
        searchField.requestFocus();
    }

    private Node buildSummary() {
        HBox summary = new HBox(24,
                stat(I18n.t("payments.summary.total"), totalValue), verticalLine(),
                stat(I18n.t("payments.summary.count"), countValue), verticalLine(),
                stat(I18n.t("payments.summary.average"), averageValue));
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.getStyleClass().add("glass-panel");
        summary.setPadding(new Insets(18, 24, 18, 24));
        return summary;
    }

    private VBox stat(String caption, Label value) {
        value.getStyleClass().add("text-success");
        return new VBox(2, Ui.label(caption, "stat-caption"), value);
    }

    private Node verticalLine() {
        Region line = new Region();
        line.getStyleClass().add("vertical-hairline");
        line.setPrefHeight(36);
        line.setMaxHeight(36);
        return line;
    }

    private Node buildPanel() {
        HBox searchBox = Ui.searchBox(searchField, I18n.t("payments.search"));
        searchBox.setPrefWidth(260);
        searchField.textProperty().addListener((obs, oldText, text) -> applyFilter());

        ToggleGroup chips = new ToggleGroup();
        HBox chipRow = new HBox(4);
        for (Period value : Period.values()) {
            ToggleButton chip = new ToggleButton(I18n.t("payments.period." + value.name()));
            chip.getStyleClass().add("filter-chip");
            chip.setToggleGroup(chips);
            chip.setSelected(value == Period.ALL);
            chip.setOnAction(event -> {
                chip.setSelected(true);
                period = value;
                applyFilter();
            });
            chipRow.getChildren().add(chip);
        }

        HBox toolbar = new HBox(12, searchBox, chipRow);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        buildTable();
        VBox.setVgrow(tableArea, Priority.ALWAYS);
        VBox panel = new VBox(14, toolbar, tableArea);
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(18, 18, 8, 18));
        return panel;
    }

    private void buildTable() {
        TableColumn<PaymentEntry, LocalDate> dateColumn = new TableColumn<>(I18n.t("payments.col.date"));
        dateColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().payment().getPaymentDate()));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Formats.date(item));
            }
        });
        dateColumn.setPrefWidth(120);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);

        TableColumn<PaymentEntry, PaymentEntry> debtColumn = new TableColumn<>(I18n.t("payments.col.debt"));
        debtColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        debtColumn.setComparator(Comparator.comparing(entry -> entry.debtTitle().toLowerCase(Locale.ROOT)));
        debtColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(PaymentEntry item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : new VBox(1, Ui.label(item.debtTitle(), "ledger-title"),
                        Ui.label(item.creditorName(), "helper")));
            }
        });
        debtColumn.setPrefWidth(280);

        TableColumn<PaymentEntry, BigDecimal> amountColumn = new TableColumn<>(I18n.t("payments.col.amount"));
        amountColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().payment().getAmount()));
        amountColumn.getStyleClass().add("amount-column");
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "+" + Formats.money(item));
                getStyleClass().remove("amount-cell");
                getStyleClass().add("amount-cell");
            }
        });
        amountColumn.setPrefWidth(150);

        TableColumn<PaymentEntry, String> notesColumn = new TableColumn<>(I18n.t("form.notes"));
        notesColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().payment().getNotes()));
        notesColumn.setPrefWidth(220);

        table.getColumns().setAll(List.of(dateColumn, debtColumn, amountColumn, notesColumn));
        table.getSortOrder().add(dateColumn);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        SortedList<PaymentEntry> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(Ui.emptyState(Icons.SEARCH, I18n.t("payments.noMatches.title"),
                I18n.t("payments.noMatches.text"), null));
        table.setOnKeyPressed(event -> {
            PaymentEntry selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && event.getCode() == KeyCode.DELETE) {
                confirmDelete(selected);
            }
        });
        Ui.tooltip(table, I18n.t("payments.deleteHint"));
    }

    private void load() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        tableArea.getChildren().setAll(progress);
        BackgroundTask.run(debtService::getPaymentEntries,
                loaded -> {
                    payments.setAll(loaded);
                    applyFilter();
                    if (loaded.isEmpty()) {
                        Button add = new Button(I18n.t("payments.new"), Icons.create(Icons.ADD, 13));
                        add.setOnAction(event -> createNew());
                        tableArea.getChildren().setAll(Ui.emptyState(Icons.PAYMENT, I18n.t("payments.empty.title"),
                                I18n.t("payments.empty.text"), add));
                    } else {
                        tableArea.getChildren().setAll(table);
                    }
                },
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
                    Button retry = new Button(I18n.t("common.retry"), Icons.create(Icons.REFRESH, 13));
                    retry.setOnAction(event -> load());
                    tableArea.getChildren().setAll(new VBox(12, error, retry));
                });
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        LocalDate from = switch (period) {
            case ALL -> null;
            case MONTH -> today.withDayOfMonth(1);
            case QUARTER -> today.minusMonths(3);
            case YEAR -> today.minusYears(1);
        };
        filtered.setPredicate(entry -> (from == null || !entry.payment().getPaymentDate().isBefore(from))
                && (query.isEmpty()
                || entry.debtTitle().toLowerCase(Locale.ROOT).contains(query)
                || entry.creditorName().toLowerCase(Locale.ROOT).contains(query)
                || (entry.payment().getNotes() != null
                && entry.payment().getNotes().toLowerCase(Locale.ROOT).contains(query))));
        updateSummary();
    }

    private void updateSummary() {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentEntry entry : filtered) {
            total = total.add(entry.payment().getAmount());
        }
        totalValue.setText(Formats.money(total));
        countValue.setText(String.valueOf(filtered.size()));
        averageValue.setText(filtered.isEmpty() ? "—"
                : Formats.money(total.divide(BigDecimal.valueOf(filtered.size()), 2, RoundingMode.HALF_UP)));
    }

    private void confirmDelete(PaymentEntry entry) {
        host.confirm(I18n.t("payments.delete.title"),
                I18n.t("payments.delete.text", Formats.money(entry.payment().getAmount()),
                        Formats.date(entry.payment().getPaymentDate())),
                I18n.t("common.delete"), true,
                () -> BackgroundTask.run(() -> {
                            debtService.deletePayment(entry.payment());
                            return null;
                        },
                        result -> {
                            host.toast(I18n.t("toast.paymentDeleted"), false);
                            load();
                        },
                        message -> host.toast(message, true)));
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("common.exportCsv"));
        chooser.setInitialFileName("kvit-payments-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        List<PaymentEntry> rows = List.copyOf(table.getItems());
        BackgroundTask.run(() -> {
                    Files.writeString(file.toPath(), new CsvExporter().payments(rows), StandardCharsets.UTF_8);
                    return null;
                },
                result -> host.toast(I18n.t("toast.exported", file.getName()), false),
                message -> host.toast(message, true));
    }
}
