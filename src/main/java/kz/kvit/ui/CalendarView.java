package kz.kvit.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import kz.kvit.i18n.I18n;
import kz.kvit.model.CalendarData;
import kz.kvit.model.DateRange;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;
import kz.kvit.model.PaymentEntry;
import kz.kvit.service.DebtService;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Month calendar with due dates of debts and the payments made.
 * A day or a period can be selected: click a day, drag across days, or click the first day
 * and Shift+click the last one. Presets and Esc help to get back quickly.
 * The panel on the right sums up everything inside the selected period.
 */
public class CalendarView implements Page {
    private static final int MAX_MARKS_PER_DAY = 2;
    private static final int NEXT_DAYS = 30;

    private final DebtService debtService;
    private final GridPane grid = new GridPane();
    private final ComboBox<Month> monthBox = new ComboBox<>();
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final VBox selectionPanel = new VBox(12);
    private final StackPane gridArea = new StackPane();
    private final VBox root;

    private final Map<LocalDate, List<DebtOverview>> dueByDay = new HashMap<>();
    private final Map<LocalDate, List<PaymentEntry>> paymentsByDay = new HashMap<>();
    private final Map<LocalDate, VBox> cellsByDay = new HashMap<>();

    private YearMonth month = YearMonth.now();
    private DateRange selection = DateRange.day(LocalDate.now());
    // The day where the current selection started; drag and Shift+click extend from here.
    private LocalDate anchor = LocalDate.now();
    // Set while the month/year boxes are filled from code, so their listeners do not react.
    private boolean updatingControls;

    public CalendarView(DebtService debtService) {
        this.debtService = debtService;
        root = Ui.page(I18n.t("calendar.eyebrow"), I18n.t("calendar.title"), null);

        VBox calendarPanel = new VBox(14, navigationRow(), presetsRow(), legend(), gridArea);
        calendarPanel.getStyleClass().add("glass-panel");
        calendarPanel.setPadding(new Insets(20, 22, 22, 22));
        VBox.setVgrow(gridArea, Priority.ALWAYS);

        selectionPanel.getStyleClass().add("glass-panel");
        selectionPanel.setPadding(new Insets(22));
        selectionPanel.setPrefWidth(330);
        selectionPanel.setMinWidth(300);

        HBox body = new HBox(20, calendarPanel, selectionPanel);
        HBox.setHgrow(calendarPanel, Priority.ALWAYS);
        root.getChildren().add(body);

        // Esc always brings the selection back to today.
        root.setFocusTraversable(true);
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                select(DateRange.day(LocalDate.now()), true);
                event.consume();
            }
        });
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

    // ------------------------------------------------------------------ Controls

    private HBox navigationRow() {
        monthBox.setItems(FXCollections.observableArrayList(Month.values()));
        monthBox.setCellFactory(list -> new MonthCell());
        monthBox.setButtonCell(new MonthCell());
        monthBox.valueProperty().addListener((obs, oldMonth, newMonth) -> {
            if (!updatingControls && newMonth != null) {
                showMonth(YearMonth.of(month.getYear(), newMonth));
            }
        });
        yearBox.valueProperty().addListener((obs, oldYear, newYear) -> {
            if (!updatingControls && newYear != null) {
                showMonth(YearMonth.of(newYear, month.getMonth()));
            }
        });
        fillYears(new TreeSet<>());

        Button previous = new Button(null, Icons.create(Icons.CHEVRON_LEFT, 12));
        previous.getStyleClass().add("icon-button");
        Ui.tooltip(previous, I18n.t("calendar.previous"));
        previous.setOnAction(event -> showMonth(month.minusMonths(1)));
        Button next = new Button(null, Icons.create(Icons.CHEVRON_RIGHT, 12));
        next.getStyleClass().add("icon-button");
        Ui.tooltip(next, I18n.t("calendar.next"));
        next.setOnAction(event -> showMonth(month.plusMonths(1)));

        HBox row = new HBox(8, monthBox, yearBox, Ui.hSpacer(), previous, next);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox presetsRow() {
        LocalDate today = LocalDate.now();
        HBox row = new HBox(6,
                preset(I18n.t("calendar.preset.today"), DateRange.day(today)),
                preset(I18n.t("calendar.preset.week"),
                        DateRange.between(today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.SUNDAY))),
                preset(I18n.t("calendar.preset.month"),
                        DateRange.between(today.withDayOfMonth(1), YearMonth.from(today).atEndOfMonth())),
                preset(I18n.t("calendar.preset.next30"), DateRange.between(today, today.plusDays(NEXT_DAYS - 1))));
        Label hint = Ui.wrapped(I18n.t("calendar.hint"), "helper");
        HBox.setHgrow(hint, Priority.ALWAYS);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setAlignment(Pos.CENTER_RIGHT);
        hint.setStyle("-fx-text-alignment: right;");
        row.getChildren().add(hint);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button preset(String text, DateRange range) {
        Button button = new Button(text);
        button.getStyleClass().add("filter-chip");
        button.setOnAction(event -> select(range, true));
        return button;
    }

    /** Years in the drop-down: a few around today plus every year that has data. */
    private void fillYears(TreeSet<Integer> yearsWithData) {
        TreeSet<Integer> years = new TreeSet<>(yearsWithData);
        int current = LocalDate.now().getYear();
        for (int year = current - 3; year <= current + 3; year++) {
            years.add(year);
        }
        updatingControls = true;
        yearBox.setItems(FXCollections.observableArrayList(years));
        yearBox.setValue(month.getYear());
        updatingControls = false;
    }

    private HBox legend() {
        HBox legend = new HBox(16,
                mark("danger", I18n.t("calendar.legend.overdue")),
                mark("active", I18n.t("calendar.legend.due")),
                mark("success", I18n.t("calendar.legend.payment")));
        legend.setAlignment(Pos.CENTER_LEFT);
        return legend;
    }

    // ------------------------------------------------------------------ Data

    private void load() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        gridArea.getChildren().setAll(progress);
        BackgroundTask.run(debtService::getCalendarData, this::index,
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
                    gridArea.getChildren().setAll(error);
                });
    }

    private void index(CalendarData data) {
        dueByDay.clear();
        paymentsByDay.clear();
        TreeSet<Integer> years = new TreeSet<>();
        for (DebtOverview overview : data.debts()) {
            LocalDate due = overview.debt().getDueDate();
            if (due != null && overview.debt().getStatus() != DebtStatus.CANCELLED) {
                dueByDay.computeIfAbsent(due, day -> new ArrayList<>()).add(overview);
                years.add(due.getYear());
            }
        }
        for (PaymentEntry entry : data.payments()) {
            LocalDate date = entry.payment().getPaymentDate();
            paymentsByDay.computeIfAbsent(date, day -> new ArrayList<>()).add(entry);
            years.add(date.getYear());
        }
        fillYears(years);
        gridArea.getChildren().setAll(grid);
        showMonth(month);
    }

    // ------------------------------------------------------------------ Month grid

    private void showMonth(YearMonth newMonth) {
        month = newMonth;
        updatingControls = true;
        monthBox.setValue(month.getMonth());
        if (!yearBox.getItems().contains(month.getYear())) {
            yearBox.getItems().add(month.getYear());
            FXCollections.sort(yearBox.getItems());
        }
        yearBox.setValue(month.getYear());
        updatingControls = false;

        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        cellsByDay.clear();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int column = 0; column < 7; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / 7);
            grid.getColumnConstraints().add(constraints);
            DayOfWeek weekday = DayOfWeek.of(column + 1);
            grid.add(Ui.label(weekday.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale()), "stat-caption"),
                    column, 0);
        }
        grid.getRowConstraints().add(new RowConstraints());

        // Weeks start on Monday; the grid always shows 6 weeks so its height never jumps.
        LocalDate first = month.atDay(1);
        LocalDate day = first.minusDays(first.getDayOfWeek().getValue() - 1);
        for (int week = 1; week <= 6; week++) {
            RowConstraints rowConstraints = new RowConstraints(80);
            rowConstraints.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(rowConstraints);
            for (int column = 0; column < 7; column++) {
                VBox cell = dayCell(day);
                cellsByDay.put(day, cell);
                grid.add(cell, column, week);
                day = day.plusDays(1);
            }
        }
        updateSelectionStyles();
        showSelection();
    }

    private VBox dayCell(LocalDate day) {
        VBox cell = new VBox(4, Ui.label(String.valueOf(day.getDayOfMonth()), "calendar-day-number"));
        cell.getStyleClass().add("calendar-day");
        if (!YearMonth.from(day).equals(month)) {
            cell.getStyleClass().add("other-month");
        }
        if (day.equals(LocalDate.now())) {
            cell.getStyleClass().add("today");
        }
        cell.setPadding(new Insets(6, 8, 6, 8));
        cell.setMinHeight(0);
        cell.setCursor(Cursor.HAND);

        // Press starts a selection (Shift extends it), dragging over other days stretches it.
        cell.setOnMousePressed(event -> {
            root.requestFocus();
            if (event.isShiftDown()) {
                select(DateRange.between(anchor, day), false);
            } else {
                anchor = day;
                select(DateRange.day(day), false);
            }
        });
        cell.setOnDragDetected(event -> cell.startFullDrag());
        cell.setOnMouseDragEntered(event -> select(DateRange.between(anchor, day), false));
        // A plain click on a day of the neighbouring month opens that month.
        cell.setOnMouseClicked(event -> {
            if (event.isStillSincePress() && !YearMonth.from(day).equals(month)) {
                showMonth(YearMonth.from(day));
            }
        });

        addMarks(cell, day);
        return cell;
    }

    private void addMarks(VBox cell, LocalDate day) {
        List<DebtOverview> due = dueByDay.getOrDefault(day, List.of());
        List<PaymentEntry> payments = paymentsByDay.getOrDefault(day, List.of());
        int shown = 0;
        for (DebtOverview overview : due) {
            if (shown == MAX_MARKS_PER_DAY) {
                break;
            }
            cell.getChildren().add(mark(dotStyle(overview), overview.debt().getTitle()));
            shown++;
        }
        if (!payments.isEmpty() && shown < MAX_MARKS_PER_DAY) {
            cell.getChildren().add(mark("success", "+" + Formats.compact(sum(payments))));
            shown++;
        }
        int hidden = due.size() + (payments.isEmpty() ? 0 : 1) - shown;
        if (hidden > 0) {
            cell.getChildren().add(Ui.label(I18n.t("calendar.more", hidden), "helper"));
        }
    }

    private HBox mark(String dotStyle, String text) {
        Circle dot = new Circle(3);
        dot.getStyleClass().addAll("status-dot", dotStyle);
        Label label = Ui.label(text, "calendar-mark");
        label.setMinWidth(0);
        HBox mark = new HBox(5, dot, label);
        mark.setAlignment(Pos.CENTER_LEFT);
        return mark;
    }

    private static String dotStyle(DebtOverview overview) {
        if (overview.overdue()) {
            return "danger";
        }
        return overview.debt().getStatus() == DebtStatus.PAID || overview.remaining().signum() <= 0 ? "success" : "active";
    }

    // ------------------------------------------------------------------ Selection

    /**
     * Selects a period. With jumpToStart the calendar also opens the month where the period
     * starts (used by presets and Esc), so the selection is always visible.
     */
    private void select(DateRange range, boolean jumpToStart) {
        selection = range;
        if (jumpToStart) {
            anchor = range.start();
            showMonth(YearMonth.from(range.start()));
            return;
        }
        updateSelectionStyles();
        showSelection();
    }

    /** Only style classes change, the cells stay the same: dragging keeps working smoothly. */
    private void updateSelectionStyles() {
        cellsByDay.forEach((day, cell) -> {
            cell.getStyleClass().removeAll("selected", "in-range");
            if (selection.contains(day)) {
                cell.getStyleClass().add(selection.isSingleDay() ? "selected" : "in-range");
            }
        });
    }

    private void showSelection() {
        List<LocalDate> dueDays = new ArrayList<>();
        List<DebtOverview> due = new ArrayList<>();
        List<PaymentEntry> payments = new ArrayList<>();
        for (LocalDate day = selection.start(); !day.isAfter(selection.end()); day = day.plusDays(1)) {
            for (DebtOverview overview : dueByDay.getOrDefault(day, List.of())) {
                dueDays.add(day);
                due.add(overview);
            }
            payments.addAll(paymentsByDay.getOrDefault(day, List.of()));
        }
        payments.sort(Comparator.comparing(entry -> entry.payment().getPaymentDate()));

        String eyebrow = selection.isSingleDay()
                ? (selection.start().equals(LocalDate.now()) ? I18n.t("calendar.preset.today") : I18n.t("calendar.day"))
                : I18n.t("calendar.period", selection.lengthInDays());
        Button reset = new Button(I18n.t("calendar.reset"));
        reset.getStyleClass().add("quiet");
        Ui.tooltip(reset, "Esc");
        reset.setOnAction(event -> select(DateRange.day(LocalDate.now()), true));
        HBox header = new HBox(8, Ui.eyebrow(eyebrow), Ui.hSpacer(), reset);
        header.setAlignment(Pos.CENTER_LEFT);

        selectionPanel.getChildren().setAll(header, Ui.wrapped(Formats.dateRange(selection), "heading-sm"));

        if (!selection.isSingleDay()) {
            HBox stats = new HBox(20,
                    new VBox(2, Ui.label(I18n.t("calendar.summary.due"), "stat-caption"),
                            Ui.label(String.valueOf(due.size()), "stat-value")),
                    new VBox(2, Ui.label(I18n.t("calendar.summary.paid"), "stat-caption"),
                            Ui.label(Formats.money(sum(payments)), "stat-value", "text-success")));
            selectionPanel.getChildren().add(stats);
        }

        if (due.isEmpty() && payments.isEmpty()) {
            selectionPanel.getChildren().add(Ui.wrapped(
                    I18n.t(selection.isSingleDay() ? "calendar.empty" : "calendar.emptyPeriod"), "helper"));
            return;
        }
        if (!due.isEmpty()) {
            selectionPanel.getChildren().add(Ui.label(I18n.t("calendar.dueSection"), "stat-caption"));
            for (int i = 0; i < due.size(); i++) {
                selectionPanel.getChildren().add(dueRow(due.get(i), dueDays.get(i)));
            }
        }
        if (!payments.isEmpty()) {
            selectionPanel.getChildren().add(Ui.label(I18n.t("calendar.paymentsSection"), "stat-caption"));
            for (PaymentEntry entry : payments) {
                selectionPanel.getChildren().add(paymentRow(entry));
            }
        }
    }

    private HBox dueRow(DebtOverview overview, LocalDate day) {
        Circle dot = new Circle(4);
        dot.getStyleClass().addAll("status-dot", dotStyle(overview));
        String subtitle = overview.debt().getCreditorName() + (selection.isSingleDay() ? "" : " · " + Formats.date(day));
        VBox text = new VBox(1, Ui.label(overview.debt().getTitle(), "ledger-title"), Ui.label(subtitle, "helper"));
        VBox right = new VBox(3, Ui.label(Formats.money(overview.remaining()), "ledger-amount"),
                Ui.statusBadge(overview));
        right.setAlignment(Pos.CENTER_RIGHT);
        keepFullWidth(right, text);
        HBox row = new HBox(10, dot, text, Ui.hSpacer(), right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ledger-row");
        return row;
    }

    private HBox paymentRow(PaymentEntry entry) {
        String subtitle = selection.isSingleDay() ? entry.creditorName()
                : Formats.date(entry.payment().getPaymentDate()) + " · " + entry.creditorName();
        VBox text = new VBox(1, Ui.label(entry.debtTitle(), "ledger-title"), Ui.label(subtitle, "helper"));
        Label amount = Ui.label("+" + Formats.money(entry.payment().getAmount()), "ledger-amount", "text-success");
        keepFullWidth(amount, text);
        HBox row = new HBox(10, Icons.create(Icons.PAYMENT, 14), text, Ui.hSpacer(), amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ledger-row");
        return row;
    }

    /** Amounts and badges are never cut; the title column shrinks (with "...") instead. */
    private static void keepFullWidth(Region fixed, VBox shrinking) {
        fixed.setMinWidth(Region.USE_PREF_SIZE);
        if (fixed instanceof VBox box) {
            box.getChildren().forEach(child -> ((Region) child).setMinWidth(Region.USE_PREF_SIZE));
        }
        shrinking.setMinWidth(0);
        HBox.setHgrow(shrinking, Priority.ALWAYS);
    }

    private static BigDecimal sum(List<PaymentEntry> payments) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentEntry entry : payments) {
            total = total.add(entry.payment().getAmount());
        }
        return total;
    }

    /** Month names in the current language: "Октябрь", "Қазан", "October". */
    private static class MonthCell extends ListCell<Month> {
        @Override
        protected void updateItem(Month item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String name = item.getDisplayName(TextStyle.FULL_STANDALONE, I18n.locale());
            setText(name.substring(0, 1).toUpperCase(I18n.locale()) + name.substring(1));
        }
    }
}
