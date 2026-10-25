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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.Payment;
import kz.kvit.service.CsvExporter;
import kz.kvit.service.DebtService;
import kz.kvit.service.DebtValidator;
import kz.kvit.service.PayoffPlanner;
import kz.kvit.service.ValidationException;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/** Debts: searchable, filterable table on the left and details of the selected debt on the right. */
public class DebtsView implements Page {

    public enum Filter { ALL, ACTIVE, OVERDUE, PAID, CANCELLED }

    private final DebtService debtService;
    private final ModalHost host;
    private final DebtValidator validator = new DebtValidator();

    private final ObservableList<DebtOverview> debts = FXCollections.observableArrayList();
    private final FilteredList<DebtOverview> filtered = new FilteredList<>(debts);
    private final TableView<DebtOverview> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final Label countLabel = Ui.label("", "helper");
    private final StackPane tableArea = new StackPane();
    private final VBox inspector = new VBox(18);
    private final VBox root;
    private Filter filter;

    public DebtsView(DebtService debtService, ModalHost host, Filter initialFilter) {
        this.debtService = debtService;
        this.host = host;
        this.filter = initialFilter;

        Button export = new Button(I18n.t("common.exportCsv"), Icons.create(Icons.EXPORT, 13));
        export.setOnAction(event -> exportCsv());
        Button add = new Button(I18n.t("debts.new"), Icons.create(Icons.ADD, 13));
        add.setOnAction(event -> createNew());
        Ui.tooltip(add, "Ctrl+N");
        HBox actions = new HBox(8, export, add);

        root = Ui.page(I18n.t("debts.eyebrow"), I18n.t("debts.title"), actions);
        HBox body = new HBox(20, buildListPanel(), buildInspectorPanel());
        HBox.setHgrow(body.getChildren().get(0), Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        root.getChildren().add(body);
        load(null);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public void refresh() {
        DebtOverview selected = table.getSelectionModel().getSelectedItem();
        load(selected == null ? null : selected.debt().getId());
    }

    @Override
    public void createNew() {
        DebtForm.open(host, debtService, null, saved -> load(saved.getId()));
    }

    @Override
    public void focusSearch() {
        searchField.requestFocus();
    }

    // ------------------------------------------------------------------ Layout

    private Node buildListPanel() {
        HBox searchBox = Ui.searchBox(searchField, I18n.t("debts.search"));
        searchBox.setPrefWidth(260);
        searchField.textProperty().addListener((obs, oldText, text) -> applyFilter());

        ToggleGroup chips = new ToggleGroup();
        HBox chipRow = new HBox(4);
        for (Filter value : Filter.values()) {
            ToggleButton chip = new ToggleButton(I18n.t("debts.filter." + value.name()));
            chip.getStyleClass().add("filter-chip");
            chip.setToggleGroup(chips);
            chip.setSelected(value == filter);
            chip.setOnAction(event -> {
                chip.setSelected(true);
                filter = value;
                applyFilter();
            });
            chipRow.getChildren().add(chip);
        }
        chipRow.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(searchBox, Priority.ALWAYS);
        HBox searchRow = new HBox(12, searchBox, countLabel);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        countLabel.setMinWidth(Region.USE_PREF_SIZE);
        VBox toolbar = new VBox(12, searchRow, chipRow);

        buildTable();
        tableArea.getChildren().setAll(table);
        VBox.setVgrow(tableArea, Priority.ALWAYS);

        VBox panel = new VBox(14, toolbar, tableArea);
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(18, 18, 8, 18));
        panel.setMinWidth(560);
        return panel;
    }

    private void buildTable() {
        TableColumn<DebtOverview, DebtOverview> titleColumn = new TableColumn<>(I18n.t("debts.col.title"));
        titleColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        titleColumn.setComparator(Comparator.comparing(overview -> overview.debt().getTitle().toLowerCase(Locale.ROOT)));
        titleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(DebtOverview item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                VBox text = new VBox(1, Ui.label(item.debt().getTitle(), "ledger-title"),
                        Ui.label(item.debt().getCreditorName(), "helper"));
                setGraphic(text);
            }
        });
        titleColumn.setPrefWidth(230);

        TableColumn<DebtOverview, BigDecimal> amountColumn = moneyColumn("debts.col.amount",
                overview -> overview.debt().getOriginalAmount());
        TableColumn<DebtOverview, BigDecimal> remainingColumn = moneyColumn("debts.col.remaining",
                DebtOverview::remaining);

        TableColumn<DebtOverview, LocalDate> dueColumn = new TableColumn<>(I18n.t("debts.col.due"));
        dueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().debt().getDueDate()));
        dueColumn.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        dueColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Formats.date(item));
            }
        });
        dueColumn.setPrefWidth(110);

        TableColumn<DebtOverview, DebtOverview> statusColumn = new TableColumn<>(I18n.t("debts.col.status"));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        statusColumn.setComparator(Comparator.comparing(DebtsView::statusOrder));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(DebtOverview item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : Ui.statusBadge(item));
            }
        });
        statusColumn.setPrefWidth(130);

        table.getColumns().setAll(List.of(titleColumn, amountColumn, remainingColumn, dueColumn, statusColumn));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        SortedList<DebtOverview> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(Ui.emptyState(Icons.SEARCH, I18n.t("debts.noMatches.title"),
                I18n.t("debts.noMatches.text"), null));

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, item) -> showInspector(item));
        table.setOnKeyPressed(event -> {
            DebtOverview selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (event.getCode() == KeyCode.DELETE) {
                confirmDelete(selected);
            } else if (event.getCode() == KeyCode.ENTER) {
                edit(selected);
            }
        });
        table.setRowFactory(view -> {
            TableRow<DebtOverview> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
    }

    private TableColumn<DebtOverview, BigDecimal> moneyColumn(String titleKey,
                                                             Function<DebtOverview, BigDecimal> value) {
        TableColumn<DebtOverview, BigDecimal> column = new TableColumn<>(I18n.t(titleKey));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.getStyleClass().add("amount-column");
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Formats.money(item));
                getStyleClass().remove("amount-cell");
                getStyleClass().add("amount-cell");
            }
        });
        column.setPrefWidth(140);
        return column;
    }

    private Node buildInspectorPanel() {
        inspector.getStyleClass().add("glass-panel");
        inspector.setPadding(new Insets(22));
        inspector.setPrefWidth(340);
        inspector.setMinWidth(320);
        inspector.setMaxWidth(360);
        showInspector(null);
        return inspector;
    }

    // ------------------------------------------------------------------ Data

    private void load(Long selectId) {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        tableArea.getChildren().setAll(progress);

        BackgroundTask.run(debtService::getDebtOverviews,
                loaded -> {
                    debts.setAll(loaded);
                    applyFilter();
                    if (loaded.isEmpty()) {
                        Button add = new Button(I18n.t("debts.new"), Icons.create(Icons.ADD, 13));
                        add.setOnAction(event -> createNew());
                        tableArea.getChildren().setAll(Ui.emptyState(Icons.LIST, I18n.t("debts.empty.title"),
                                I18n.t("debts.empty.text"), add));
                        return;
                    }
                    tableArea.getChildren().setAll(table);
                    selectById(selectId);
                },
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
                    Button retry = new Button(I18n.t("common.retry"), Icons.create(Icons.REFRESH, 13));
                    retry.setOnAction(event -> load(selectId));
                    VBox box = new VBox(12, error, retry);
                    box.setAlignment(Pos.TOP_LEFT);
                    tableArea.getChildren().setAll(box);
                });
    }

    private void selectById(Long id) {
        if (id == null) {
            table.getSelectionModel().clearSelection();
            return;
        }
        for (DebtOverview overview : table.getItems()) {
            if (overview.debt().getId().equals(id)) {
                table.getSelectionModel().select(overview);
                table.scrollTo(overview);
                return;
            }
        }
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        Predicate<DebtOverview> byText = overview -> query.isEmpty()
                || overview.debt().getTitle().toLowerCase(Locale.ROOT).contains(query)
                || overview.debt().getCreditorName().toLowerCase(Locale.ROOT).contains(query)
                || (overview.debt().getNotes() != null && overview.debt().getNotes().toLowerCase(Locale.ROOT).contains(query));
        Predicate<DebtOverview> byStatus = overview -> switch (filter) {
            case ALL -> true;
            case ACTIVE -> overview.debt().getStatus() == DebtStatus.ACTIVE && !overview.overdue();
            case OVERDUE -> overview.overdue();
            case PAID -> overview.debt().getStatus() == DebtStatus.PAID;
            case CANCELLED -> overview.debt().getStatus() == DebtStatus.CANCELLED;
        };
        filtered.setPredicate(byText.and(byStatus));
        countLabel.setText(I18n.t("debts.count", filtered.size(), debts.size()));
    }

    /** Sort order of the status column: overdue first, cancelled last. */
    private static int statusOrder(DebtOverview overview) {
        if (overview.overdue()) {
            return 0;
        }
        return switch (overview.debt().getStatus()) {
            case ACTIVE -> 1;
            case PAID -> 2;
            case CANCELLED -> 3;
        };
    }

    // ------------------------------------------------------------------ Inspector

    private void showInspector(DebtOverview overview) {
        if (overview == null) {
            VBox empty = Ui.emptyState(Icons.INFO, I18n.t("debts.inspector.none.title"),
                    I18n.t("debts.inspector.none.text"), null);
            VBox.setVgrow(empty, Priority.ALWAYS);
            inspector.getChildren().setAll(empty);
            inspector.setAlignment(Pos.CENTER);
            return;
        }
        inspector.setAlignment(Pos.TOP_LEFT);

        Label title = Ui.wrapped(overview.debt().getTitle(), "heading-sm");
        HBox header = new HBox(10, new VBox(2, title, Ui.label(overview.debt().getCreditorName(), "helper")),
                Ui.hSpacer(), Ui.statusBadge(overview));
        header.setAlignment(Pos.TOP_LEFT);

        int percent = Formats.percent(overview.paid(), overview.debt().getOriginalAmount());
        Label remaining = Ui.label(Formats.money(overview.remaining()), "balance-number");
        remaining.setStyle("-fx-font-size: 30px;");
        VBox balance = new VBox(6, Ui.eyebrow(I18n.t("debts.inspector.remaining")), remaining,
                meter(percent / 100.0), Ui.label(I18n.t("debts.inspector.repaid", percent), "helper"));

        VBox details = new VBox(8,
                detail(I18n.t("debts.col.amount"), Formats.money(overview.debt().getOriginalAmount())),
                detail(I18n.t("debts.col.paid"), Formats.money(overview.paid())),
                detail(I18n.t("debts.col.due"), dueText(overview)),
                detail(I18n.t("form.rate"), overview.debt().getInterestRate() == null ? "—"
                        : overview.debt().getInterestRate().stripTrailingZeros().toPlainString() + " %"),
                detail(I18n.t("debts.inspector.created"), overview.debt().getCreatedAt() == null ? "—"
                        : Formats.date(overview.debt().getCreatedAt().toLocalDate())));
        if (overview.debt().getNotes() != null && !overview.debt().getNotes().isBlank()) {
            details.getChildren().add(Ui.wrapped(overview.debt().getNotes(), "body-muted"));
        }

        Button pay = new Button(I18n.t("debts.inspector.pay"), Icons.create(Icons.PAYMENT, 13));
        pay.setDisable(overview.debt().getStatus() != DebtStatus.ACTIVE || overview.remaining().signum() <= 0);
        pay.setOnAction(event -> PaymentForm.open(host, debtService, overview.debt().getId(),
                () -> load(overview.debt().getId())));
        Button edit = new Button(null, Icons.create(Icons.EDIT, 13));
        edit.getStyleClass().add("icon-button");
        Ui.tooltip(edit, I18n.t("common.edit") + " (Enter)");
        edit.setOnAction(event -> edit(overview));
        Button delete = new Button(null, Icons.create(Icons.DELETE, 13));
        delete.getStyleClass().addAll("icon-button", "danger-button");
        Ui.tooltip(delete, I18n.t("common.delete") + " (Delete)");
        delete.setOnAction(event -> confirmDelete(overview));
        HBox actions = new HBox(8, pay, Ui.hSpacer(), edit, delete);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox history = new VBox();
        Label historyTitle = Ui.label(I18n.t("debts.inspector.history"), "heading-sm");
        historyTitle.setStyle("-fx-font-size: 14px;");
        loadHistory(overview, history);

        inspector.getChildren().setAll(header, balance, Ui.hairline(), details, actions);
        if (overview.debt().getStatus() == DebtStatus.ACTIVE && overview.remaining().signum() > 0) {
            inspector.getChildren().add(planner(overview));
        }
        inspector.getChildren().addAll(Ui.hairline(), historyTitle, history);
        Motion.fadeIn(inspector, 0);
    }

    /** "What if I pay X per month?" and "how much per month to finish by the due date?" */
    private Node planner(DebtOverview overview) {
        PayoffPlanner planner = new PayoffPlanner();
        TextField monthlyField = new TextField();
        monthlyField.setPromptText(I18n.t("planner.monthly.prompt"));
        Label result = Ui.wrapped(I18n.t("planner.monthly.hint"), "helper");
        monthlyField.textProperty().addListener((obs, oldText, text) -> {
            try {
                BigDecimal monthly = validator.parseAmount(text, I18n.t("planner.monthly"), true);
                if (monthly.signum() <= 0) {
                    result.setText(I18n.t("planner.monthly.hint"));
                    return;
                }
                PayoffPlanner.Plan plan = planner.planByMonthlyPayment(overview.remaining(), monthly, LocalDate.now());
                result.setText(I18n.t("planner.monthly.result", plan.months(),
                        Formats.monthYear(YearMonth.from(plan.payoffDate()))));
            } catch (ValidationException e) {
                result.setText(I18n.t("planner.monthly.hint"));
            }
        });

        HBox title = new HBox(8, Icons.create(Icons.CALCULATOR, 13), Ui.label(I18n.t("planner.title"), "ledger-title"));
        title.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, title, monthlyField, result);

        LocalDate due = overview.debt().getDueDate();
        if (due != null && due.isAfter(LocalDate.now())) {
            BigDecimal needed = planner.monthlyPaymentToFinishBy(overview.remaining(), due, LocalDate.now());
            box.getChildren().add(Ui.wrapped(I18n.t("planner.byDue", Formats.date(due), Formats.money(needed)), "helper"));
        }
        box.getStyleClass().add("planner");
        return box;
    }

    private Node meter(double share) {
        Region fill = new Region();
        fill.getStyleClass().add("meter-fill");
        fill.setPrefHeight(6);
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("meter-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefHeight(6);
        track.setMinHeight(6);
        track.setMaxHeight(6);
        track.widthProperty().addListener((obs, oldWidth, width) -> fill.setPrefWidth(width.doubleValue() * share));
        return track;
    }

    private HBox detail(String key, String value) {
        HBox row = new HBox(8, Ui.label(key, "detail-key"), Ui.hSpacer(), Ui.label(value, "detail-value"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String dueText(DebtOverview overview) {
        LocalDate due = overview.debt().getDueDate();
        if (due == null) {
            return I18n.t("debts.noDueDate");
        }
        if (overview.remaining().signum() <= 0 || overview.debt().getStatus() != DebtStatus.ACTIVE) {
            return Formats.date(due);
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), due);
        String relative = days < 0 ? I18n.t("due.overdue", -days)
                : days == 0 ? I18n.t("due.today") : I18n.t("due.inDays", days);
        return Formats.date(due) + " · " + relative;
    }

    private void loadHistory(DebtOverview overview, VBox history) {
        history.getChildren().setAll(Ui.label(I18n.t("common.loading"), "helper"));
        BackgroundTask.run(() -> debtService.getPaymentsOfDebt(overview.debt().getId()),
                payments -> {
                    history.getChildren().clear();
                    if (payments.isEmpty()) {
                        history.getChildren().add(Ui.label(I18n.t("debts.inspector.noPayments"), "helper"));
                    }
                    for (Payment payment : payments) {
                        history.getChildren().add(historyRow(overview, payment));
                    }
                },
                message -> history.getChildren().setAll(Ui.wrapped(message, "helper", "text-danger")));
    }

    private HBox historyRow(DebtOverview overview, Payment payment) {
        VBox text = new VBox(1, Ui.label("+" + Formats.money(payment.getAmount()), "ledger-amount", "text-success"),
                Ui.label(Formats.date(payment.getPaymentDate())
                        + (payment.getNotes() == null ? "" : " · " + payment.getNotes()), "helper"));
        Button remove = new Button(null, Icons.create(Icons.DELETE, 12));
        remove.getStyleClass().addAll("quiet", "icon-button");
        Ui.tooltip(remove, I18n.t("payments.delete"));
        remove.setOnAction(event -> host.confirm(I18n.t("payments.delete.title"),
                I18n.t("payments.delete.text", Formats.money(payment.getAmount()), Formats.date(payment.getPaymentDate())),
                I18n.t("common.delete"), true,
                () -> BackgroundTask.run(() -> {
                            debtService.deletePayment(payment);
                            return null;
                        },
                        result -> {
                            host.toast(I18n.t("toast.paymentDeleted"), false);
                            load(overview.debt().getId());
                        },
                        message -> host.toast(message, true))));
        HBox row = new HBox(8, text, Ui.hSpacer(), remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ledger-row");
        row.setPadding(new Insets(7, 0, 7, 0));
        return row;
    }

    // ------------------------------------------------------------------ Actions

    private void edit(DebtOverview overview) {
        DebtForm.open(host, debtService, overview.debt(), saved -> load(saved.getId()));
    }

    private void confirmDelete(DebtOverview overview) {
        host.confirm(I18n.t("debts.delete.title"),
                I18n.t("debts.delete.text", overview.debt().getTitle()),
                I18n.t("common.delete"), true,
                () -> BackgroundTask.run(() -> {
                            debtService.deleteDebt(overview.debt().getId());
                            return null;
                        },
                        result -> {
                            host.toast(I18n.t("toast.debtDeleted"), false);
                            load(null);
                        },
                        message -> host.toast(message, true)));
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("common.exportCsv"));
        chooser.setInitialFileName("kvit-debts-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        List<DebtOverview> rows = List.copyOf(table.getItems());
        BackgroundTask.run(() -> {
                    Files.writeString(file.toPath(), new CsvExporter().debts(rows), StandardCharsets.UTF_8);
                    return null;
                },
                result -> host.toast(I18n.t("toast.exported", file.getName()), false),
                message -> host.toast(message, true));
    }
}
