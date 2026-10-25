package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DashboardData;
import kz.kvit.model.DashboardSummary;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.PaymentEntry;
import kz.kvit.service.DebtService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.function.IntConsumer;

/**
 * Dashboard laid out like a ledger: the remaining balance as the headline, a repayment meter,
 * a row of totals, then two lists — debts that need attention and the latest payments.
 */
public class DashboardView implements Page {
    private static final int DEBTS_SECTION = 1;

    private final DebtService debtService;
    private final ModalHost host;
    private final IntConsumer openSection;
    private final Runnable onShowOverdue;

    private final VBox content = new VBox(20);
    private final Button refreshButton = new Button(I18n.t("common.refresh"), Icons.create(Icons.REFRESH, 13));
    private final VBox root;
    private Animation loadingPulse;

    public DashboardView(DebtService debtService, ModalHost host, IntConsumer openSection, Runnable onShowOverdue) {
        this.debtService = debtService;
        this.host = host;
        this.openSection = openSection;
        this.onShowOverdue = onShowOverdue;

        Button newDebt = new Button(I18n.t("debts.new"), Icons.create(Icons.ADD, 13));
        newDebt.setOnAction(event -> createNew());
        Ui.tooltip(newDebt, "Ctrl+N");
        Button newPayment = new Button(I18n.t("payments.new"), Icons.create(Icons.PAYMENT, 13));
        newPayment.setOnAction(event -> PaymentForm.open(host, debtService, null, this::load));
        refreshButton.setOnAction(event -> load());
        Ui.tooltip(refreshButton, "F5");

        root = Ui.page(I18n.t("dashboard.eyebrow", Formats.longDate(LocalDate.now())), greeting(),
                new HBox(8, refreshButton, newPayment, newDebt));
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

    @Override
    public void createNew() {
        DebtForm.open(host, debtService, null, saved -> load());
    }

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 5) {
            return I18n.t("dashboard.greeting.night");
        }
        if (hour < 12) {
            return I18n.t("dashboard.greeting.morning");
        }
        if (hour < 18) {
            return I18n.t("dashboard.greeting.day");
        }
        return I18n.t("dashboard.greeting.evening");
    }

    private void load() {
        refreshButton.setDisable(true);
        content.getChildren().setAll(skeleton());
        BackgroundTask.run(debtService::getDashboardData, this::showData, this::showError);
    }

    // ------------------------------------------------------------------- States

    private Node skeleton() {
        VBox panel = new VBox(18,
                skeletonBlock(140, 12), skeletonBlock(320, 44), skeletonBlock(-1, 8),
                new HBox(24, skeletonBlock(120, 36), skeletonBlock(120, 36), skeletonBlock(120, 36)));
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(28));

        if (Motion.isEnabled()) {
            FadeTransition pulse = new FadeTransition(Duration.millis(900), panel);
            pulse.setFromValue(1);
            pulse.setToValue(0.5);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
            loadingPulse = pulse;
        }
        return panel;
    }

    private Region skeletonBlock(double width, double height) {
        Region block = new Region();
        block.getStyleClass().add("skeleton");
        block.setPrefHeight(height);
        if (width > 0) {
            block.setPrefWidth(width);
            block.setMaxWidth(width);
        }
        return block;
    }

    private void stopLoading() {
        refreshButton.setDisable(false);
        if (loadingPulse != null) {
            loadingPulse.stop();
            loadingPulse = null;
        }
    }

    private void showError(String message) {
        stopLoading();
        InfoBar error = new InfoBar();
        error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
        Button retry = new Button(I18n.t("common.retry"), Icons.create(Icons.REFRESH, 13));
        retry.setOnAction(event -> load());
        VBox panel = new VBox(16, error, retry);
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(24));
        content.getChildren().setAll(panel);
    }

    private void showData(DashboardData data) {
        stopLoading();
        DashboardSummary summary = data.summary();

        if (summary.totalOriginal().signum() == 0) {
            Button add = new Button(I18n.t("debts.new"), Icons.create(Icons.ADD, 13));
            add.setOnAction(event -> createNew());
            VBox panel = new VBox(Ui.emptyState(Icons.LIST, I18n.t("dashboard.empty.title"),
                    I18n.t("dashboard.empty.text"), add));
            panel.getStyleClass().add("glass-panel");
            panel.setPadding(new Insets(56, 32, 56, 32));
            content.getChildren().setAll(panel);
            Motion.fadeIn(panel, 0);
            return;
        }

        Node balance = balancePanel(summary);
        Node attention = attentionPanel(data);
        Node payments = paymentsPanel(data);
        HBox lists = new HBox(20, attention, payments);
        HBox.setHgrow(attention, Priority.ALWAYS);
        HBox.setHgrow(payments, Priority.ALWAYS);

        content.getChildren().setAll(balance, lists);
        if (summary.overdueCount() > 0) {
            content.getChildren().add(0, overdueBanner(data));
        }
        Motion.fadeIn(balance, 0);
        Motion.fadeIn(lists, 100);
    }

    /** Warning above everything else when some debts are overdue. */
    private Node overdueBanner(DashboardData data) {
        BigDecimal overdueSum = data.needingAttention().stream()
                .filter(DebtOverview::overdue)
                .map(DebtOverview::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Label text = Ui.wrapped(I18n.t("dashboard.overdueBanner", data.summary().overdueCount(),
                Formats.money(overdueSum)), "ledger-title");
        Button show = new Button(I18n.t("dashboard.overdueBanner.show"));
        show.setOnAction(event -> onShowOverdue.run());
        HBox banner = new HBox(12, Icons.create(Icons.WARNING, 16), text, Ui.hSpacer(), show);
        HBox.setHgrow(text, Priority.ALWAYS);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("banner");
        return banner;
    }

    // ------------------------------------------------------------------- Balance

    private Node balancePanel(DashboardSummary summary) {
        int repaidPercent = Formats.percent(summary.totalPaid(), summary.totalOriginal());

        VBox headline = new VBox(4,
                Ui.eyebrow(I18n.t("dashboard.remaining")),
                Ui.label(Formats.money(summary.totalRemaining()), "balance-number"),
                Ui.label(I18n.t("dashboard.ofTotal", Formats.money(summary.totalOriginal())), "helper"));

        Label percent = Ui.label(repaidPercent + "%", "hero-percent");
        VBox repaid = new VBox(0, percent, Ui.label(I18n.t("hero.repaid"), "helper"));
        repaid.setAlignment(Pos.CENTER_RIGHT);

        HBox top = new HBox(24, headline, Ui.hSpacer(), repaid);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(24,
                stat(I18n.t("dashboard.stat.total"), Formats.money(summary.totalOriginal()), null),
                verticalLine(),
                stat(I18n.t("dashboard.stat.paid"), Formats.money(summary.totalPaid()), "text-success"),
                verticalLine(),
                stat(I18n.t("dashboard.stat.active"), String.valueOf(summary.activeCount()), null),
                verticalLine(),
                stat(I18n.t("dashboard.stat.overdue"), String.valueOf(summary.overdueCount()),
                        summary.overdueCount() > 0 ? "text-danger" : null));
        stats.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(20, top, meter(repaidPercent / 100.0), stats,
                Ui.wrapped(I18n.t("dashboard.note"), "helper"));
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(28));
        return panel;
    }

    /** Repayment meter; the fill grows to its value once when the dashboard opens. */
    private Node meter(double share) {
        Region fill = new Region();
        fill.getStyleClass().add("meter-fill");
        fill.setPrefHeight(8);
        fill.setMaxWidth(Region.USE_PREF_SIZE);

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("meter-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPrefHeight(8);
        track.setMinHeight(8);
        track.setMaxHeight(8);

        track.widthProperty().addListener((obs, oldWidth, width) -> {
            double target = width.doubleValue() * share;
            if (oldWidth.doubleValue() == 0 && Motion.isEnabled()) {
                new Timeline(new KeyFrame(Duration.millis(900),
                        new KeyValue(fill.prefWidthProperty(), target, Interpolator.SPLINE(0.2, 0.8, 0.2, 1))))
                        .play();
            } else {
                fill.setPrefWidth(target);
            }
        });
        fill.setPrefWidth(0);
        return track;
    }

    private VBox stat(String caption, String value, String valueStyle) {
        Label valueLabel = Ui.label(value, "stat-value");
        if (valueStyle != null) {
            valueLabel.getStyleClass().add(valueStyle);
        }
        return new VBox(2, Ui.label(caption, "stat-caption"), valueLabel);
    }

    private Region verticalLine() {
        Region line = new Region();
        line.getStyleClass().add("vertical-hairline");
        line.setPrefHeight(36);
        line.setMaxHeight(36);
        return line;
    }

    // ------------------------------------------------------------------- Lists

    private Node attentionPanel(DashboardData data) {
        VBox list = new VBox();
        for (DebtOverview overview : data.needingAttention()) {
            list.getChildren().add(attentionRow(overview));
        }
        Node body = list.getChildren().isEmpty()
                ? Ui.emptyState(Icons.CHECK, I18n.t("dashboard.attention.none.title"),
                        I18n.t("dashboard.attention.none.text"), null)
                : list;
        return listPanel(I18n.t("dashboard.attention.title"), data.needingAttention().size(), body);
    }

    private Node attentionRow(DebtOverview overview) {
        LocalDate dueDate = overview.debt().getDueDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), dueDate);

        Circle dot = new Circle(4);
        dot.getStyleClass().addAll("status-dot", overview.overdue() ? "danger" : "active");

        VBox text = new VBox(2,
                Ui.label(overview.debt().getTitle(), "ledger-title"),
                Ui.label(overview.debt().getCreditorName() + " · " + Formats.date(dueDate), "helper"));

        String when = days < 0 ? I18n.t("due.overdue", -days) : days == 0 ? I18n.t("due.today") : I18n.t("due.inDays", days);
        Label badge = Ui.label(when, "badge", overview.overdue() ? "danger" : "info");
        VBox right = new VBox(4, Ui.label(Formats.money(overview.remaining()), "ledger-amount"), badge);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, dot, text, Ui.hSpacer(), right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ledger-row");
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(event -> openSection.accept(DEBTS_SECTION));
        Ui.tooltip(row, I18n.t("dashboard.attention.open"));
        return row;
    }

    private Node paymentsPanel(DashboardData data) {
        VBox list = new VBox();
        for (PaymentEntry entry : data.recentPayments()) {
            VBox text = new VBox(2,
                    Ui.label(entry.debtTitle(), "ledger-title"),
                    Ui.label(Formats.date(entry.payment().getPaymentDate()), "helper"));
            Label amount = Ui.label("+" + Formats.money(entry.payment().getAmount()), "ledger-amount", "text-success");

            HBox row = new HBox(12, Icons.create(Icons.PAYMENT, 14), text, Ui.hSpacer(), amount);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ledger-row");
            list.getChildren().add(row);
        }
        Node body = list.getChildren().isEmpty()
                ? Ui.emptyState(Icons.PAYMENT, I18n.t("dashboard.payments.none.title"),
                        I18n.t("dashboard.payments.none.text"), null)
                : list;
        return listPanel(I18n.t("dashboard.payments.title"), data.recentPayments().size(), body);
    }

    private VBox listPanel(String title, int count, Node body) {
        HBox header = new HBox(10, Ui.label(title, "heading-sm"), Ui.label(String.valueOf(count), "badge"));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(body, new Insets(4, 0, 0, 0));

        VBox panel = new VBox(10, header, body);
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(22, 24, 18, 24));
        panel.setPrefWidth(0);
        return panel;
    }
}
