package kz.kvit.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kz.kvit.i18n.I18n;
import kz.kvit.model.CreditorTotal;
import kz.kvit.model.DashboardSummary;
import kz.kvit.model.MonthTotal;
import kz.kvit.model.PayoffForecast;
import kz.kvit.model.ReportData;
import kz.kvit.model.StatusBreakdown;
import kz.kvit.service.DebtService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Reports: payments per month, remaining amount per creditor, status breakdown and a payoff estimate.
 * Charts follow simple rules: one color per chart, thin bars with a rounded end, quiet grid lines,
 * a value label only on the important bar, and a tooltip with the exact value on every bar.
 */
public class ReportsView implements Page {
    private static final double CHART_HEIGHT = 180;
    private static final double BAR_WIDTH = 22;

    private final DebtService debtService;
    private final VBox content = new VBox(20);
    private final VBox root;

    public ReportsView(DebtService debtService) {
        this.debtService = debtService;
        Button refresh = new Button(I18n.t("common.refresh"), Icons.create(Icons.REFRESH, 13));
        refresh.setOnAction(event -> refresh());
        Ui.tooltip(refresh, "F5");
        root = Ui.page(I18n.t("reports.eyebrow"), I18n.t("reports.title"), refresh);
        root.getChildren().add(content);
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

    private void load() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        content.getChildren().setAll(progress);
        BackgroundTask.run(debtService::getReportData, this::show,
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
                    Button retry = new Button(I18n.t("common.retry"), Icons.create(Icons.REFRESH, 13));
                    retry.setOnAction(event -> load());
                    content.getChildren().setAll(new VBox(12, error, retry));
                });
    }

    private void show(ReportData data) {
        if (data.statuses().total() == 0) {
            VBox empty = new VBox(Ui.emptyState(Icons.CHART, I18n.t("reports.empty.title"),
                    I18n.t("reports.empty.text"), null));
            empty.getStyleClass().add("glass-panel");
            empty.setPadding(new Insets(56, 32, 56, 32));
            content.getChildren().setAll(empty);
            return;
        }

        Node months = panel(I18n.t("reports.months.title"), I18n.t("reports.months.subtitle"),
                columnChart(data.paymentsByMonth()));
        Node forecast = panel(I18n.t("reports.forecast.title"), I18n.t("reports.forecast.subtitle"),
                forecast(data.forecast(), data.summary()));
        HBox firstRow = new HBox(20, months, forecast);
        HBox.setHgrow(months, Priority.ALWAYS);
        ((Region) forecast).setPrefWidth(320);
        ((Region) forecast).setMinWidth(300);

        Node creditors = panel(I18n.t("reports.creditors.title"), I18n.t("reports.creditors.subtitle"),
                creditorBars(data.byCreditor()));
        Node statuses = panel(I18n.t("reports.statuses.title"), I18n.t("reports.statuses.subtitle"),
                statusBar(data.statuses()));
        HBox secondRow = new HBox(20, creditors, statuses);
        HBox.setHgrow(creditors, Priority.ALWAYS);
        HBox.setHgrow(statuses, Priority.ALWAYS);

        content.getChildren().setAll(firstRow, secondRow);
        Motion.fadeIn(firstRow, 0);
        Motion.fadeIn(secondRow, 100);
    }

    private VBox panel(String title, String subtitle, Node body) {
        VBox panel = new VBox(6, Ui.label(title, "heading-sm"), Ui.wrapped(subtitle, "helper"), body);
        VBox.setMargin(body, new Insets(14, 0, 0, 0));
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(22, 24, 22, 24));
        panel.setPrefWidth(0);
        return panel;
    }

    // ------------------------------------------------------------------ Column chart

    private Node columnChart(List<MonthTotal> months) {
        BigDecimal maxValue = months.stream().map(MonthTotal::total).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (maxValue.signum() == 0) {
            return Ui.emptyState(Icons.PAYMENT, I18n.t("reports.months.empty.title"),
                    I18n.t("reports.months.empty.text"), null);
        }
        BigDecimal scaleMax = niceMax(maxValue);
        YearMonth current = YearMonth.now();

        // Grid: three quiet lines (max, half, zero) with compact values on the left.
        VBox grid = new VBox();
        VBox yAxis = new VBox();
        for (int i = 0; i < 3; i++) {
            Region line = new Region();
            line.getStyleClass().add(i == 2 ? "chart-baseline" : "chart-gridline");
            BigDecimal tick = scaleMax.multiply(BigDecimal.valueOf(2 - i)).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
            Label tickLabel = Ui.label(Formats.compact(tick), "chart-axis-label");
            tickLabel.setMinHeight(14);
            tickLabel.setTranslateY(-7);
            grid.getChildren().add(line);
            yAxis.getChildren().add(tickLabel);
            if (i < 2) {
                Region gap = new Region();
                VBox.setVgrow(gap, Priority.ALWAYS);
                grid.getChildren().add(gap);
                Region labelGap = new Region();
                VBox.setVgrow(labelGap, Priority.ALWAYS);
                yAxis.getChildren().add(labelGap);
            }
        }
        grid.setPrefHeight(CHART_HEIGHT);
        yAxis.setPrefHeight(CHART_HEIGHT);
        yAxis.setMinWidth(Region.USE_PREF_SIZE);
        yAxis.setAlignment(Pos.TOP_RIGHT);

        HBox bars = new HBox();
        HBox labels = new HBox();
        MonthTotal highest = months.stream().max((a, b) -> a.total().compareTo(b.total())).orElseThrow();
        for (MonthTotal month : months) {
            double height = month.total().doubleValue() / scaleMax.doubleValue() * CHART_HEIGHT;
            Region bar = new Region();
            bar.getStyleClass().add("chart-bar");
            if (month.month().equals(current)) {
                bar.getStyleClass().add("current");
            }
            bar.setPrefSize(BAR_WIDTH, Math.max(height, month.total().signum() > 0 ? 3 : 0));
            bar.setMaxSize(BAR_WIDTH, Region.USE_PREF_SIZE);
            Ui.tooltip(bar, Formats.monthYear(month.month()) + "\n" + Formats.money(month.total()));

            VBox slot = new VBox(4);
            slot.setAlignment(Pos.BOTTOM_CENTER);
            // Only the highest month gets its value written above the bar.
            if (month == highest) {
                slot.getChildren().add(Ui.label(Formats.compact(month.total()), "chart-axis-label"));
            }
            slot.getChildren().add(bar);
            HBox.setHgrow(slot, Priority.ALWAYS);
            slot.setMaxWidth(Double.MAX_VALUE);
            bars.getChildren().add(slot);

            Label monthLabel = Ui.label(Formats.monthShort(month.month()), "chart-axis-label");
            StackPane labelSlot = new StackPane(monthLabel);
            HBox.setHgrow(labelSlot, Priority.ALWAYS);
            labelSlot.setMaxWidth(Double.MAX_VALUE);
            labels.getChildren().add(labelSlot);
        }
        bars.setAlignment(Pos.BOTTOM_LEFT);
        bars.setPrefHeight(CHART_HEIGHT);

        StackPane plot = new StackPane(grid, bars);
        VBox plotWithLabels = new VBox(6, plot, labels);
        HBox.setHgrow(plotWithLabels, Priority.ALWAYS);
        HBox chart = new HBox(10, yAxis, plotWithLabels);
        chart.setAlignment(Pos.TOP_LEFT);
        return chart;
    }

    /** Rounds the maximum up to 1, 2 or 5 times a power of ten, so the grid shows clean numbers. */
    static BigDecimal niceMax(BigDecimal value) {
        double max = value.doubleValue();
        double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
        for (double step : new double[]{1, 2, 5, 10}) {
            if (step * magnitude >= max) {
                return BigDecimal.valueOf(step * magnitude);
            }
        }
        return value;
    }

    // ------------------------------------------------------------------ Creditors

    private Node creditorBars(List<CreditorTotal> creditors) {
        BigDecimal max = creditors.stream().map(CreditorTotal::remaining).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (creditors.isEmpty()) {
            return Ui.label(I18n.t("reports.creditors.empty"), "helper");
        }
        VBox rows = new VBox(12);
        for (CreditorTotal creditor : creditors) {
            String name = creditor.creditorName() == null ? I18n.t("reports.creditors.other") : creditor.creditorName();
            Label nameLabel = Ui.label(name, "ledger-title");
            nameLabel.setMinWidth(130);
            nameLabel.setPrefWidth(130);

            double share = max.signum() == 0 ? 0 : creditor.remaining().doubleValue() / max.doubleValue();
            Region fill = new Region();
            fill.getStyleClass().add("hbar-fill");
            fill.setPrefHeight(14);
            fill.setMaxWidth(Region.USE_PREF_SIZE);
            Ui.tooltip(fill, name + "\n" + I18n.t("debts.col.remaining") + ": " + Formats.money(creditor.remaining())
                    + "\n" + I18n.t("debts.col.amount") + ": " + Formats.money(creditor.original())
                    + "\n" + I18n.t("reports.creditors.debts", creditor.debtCount()));
            StackPane track = new StackPane(fill);
            track.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);
            track.widthProperty().addListener((obs, oldWidth, width) ->
                    fill.setPrefWidth(Math.max(share * width.doubleValue(), creditor.remaining().signum() > 0 ? 3 : 0)));

            Label value = Ui.label(Formats.money(creditor.remaining()), "detail-value");
            value.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(12, nameLabel, track, value);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------ Statuses

    private Node statusBar(StatusBreakdown statuses) {
        record Part(String key, String style, int count) {
        }
        List<Part> parts = List.of(
                new Part("status.OVERDUE", "segment-overdue", statuses.overdue()),
                new Part("status.ACTIVE", "segment-active", statuses.active()),
                new Part("status.PAID", "segment-paid", statuses.paid()),
                new Part("status.CANCELLED", "segment-cancelled", statuses.cancelled()));

        // 100 % stacked bar; the 2 px spacing between segments separates them (no outlines).
        HBox bar = new HBox(2);
        bar.setPrefHeight(18);
        VBox legend = new VBox(8);
        for (Part part : parts) {
            if (part.count() > 0) {
                Region segment = new Region();
                segment.getStyleClass().add(part.style());
                segment.setPrefHeight(18);
                HBox.setHgrow(segment, Priority.ALWAYS);
                segment.setPrefWidth(part.count() * 1000.0 / statuses.total());
                segment.setMaxWidth(Double.MAX_VALUE);
                Ui.tooltip(segment, I18n.t(part.key()) + ": " + part.count());
                bar.getChildren().add(segment);
            }
            Region swatch = new Region();
            swatch.getStyleClass().addAll("legend-swatch", part.style());
            int percent = Math.round(part.count() * 100f / statuses.total());
            HBox legendRow = new HBox(10, swatch, Ui.label(I18n.t(part.key()), "detail-key"), Ui.hSpacer(),
                    Ui.label(part.count() + "  ·  " + percent + " %", "detail-value"));
            legendRow.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(legendRow);
        }
        // Round only the outer ends of the bar.
        if (!bar.getChildren().isEmpty()) {
            bar.getChildren().get(0).setStyle("-fx-background-radius: 4 0 0 4;");
            Node last = bar.getChildren().get(bar.getChildren().size() - 1);
            last.setStyle(bar.getChildren().size() == 1 ? "-fx-background-radius: 4;" : "-fx-background-radius: 0 4 4 0;");
        }

        Label total = Ui.label(I18n.t("reports.statuses.total", statuses.total()), "helper");
        return new VBox(16, bar, legend, total);
    }

    // ------------------------------------------------------------------ Forecast

    private Node forecast(PayoffForecast forecast, DashboardSummary summary) {
        if (summary.totalRemaining().signum() <= 0) {
            return Ui.emptyState(Icons.CHECK, I18n.t("reports.forecast.done.title"),
                    I18n.t("reports.forecast.done.text"), null);
        }
        if (forecast == null) {
            return Ui.wrapped(I18n.t("reports.forecast.noData"), "body-muted");
        }
        Label date = Ui.label(Formats.monthYear(YearMonth.from(forecast.estimatedDate())), "balance-number");
        date.setStyle("-fx-font-size: 32px;");
        return new VBox(10,
                Ui.eyebrow(I18n.t("reports.forecast.eta")),
                date,
                Ui.wrapped(I18n.t("reports.forecast.text", forecast.monthsLeft(),
                        Formats.money(forecast.averageMonthlyPayment())), "body-muted"),
                Ui.wrapped(I18n.t("reports.forecast.note"), "helper"));
    }
}
