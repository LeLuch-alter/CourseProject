package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import kz.kvit.i18n.I18n;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Animated hero of the login screen: a "repayment ring" that fills up to 68 %
 * and floating cards of a sample debt.
 * The numbers are decorative examples, not the user's data.
 * Drawn on a fixed 520 x 360 stage that is scaled to the available space.
 */
public class LoginHero extends StackPane {
    private static final double WIDTH = 520;
    private static final double HEIGHT = 360;
    private static final double CENTER_X = WIDTH / 2;
    private static final double CENTER_Y = HEIGHT / 2;
    private static final double RING_RADIUS = 92;
    private static final double REPAID_SHARE = 0.68;

    private final Theme theme = Theme.current();
    private final Pane stage = new Pane();
    private final List<Animation> animations = new ArrayList<>();
    private final boolean animated = Motion.isEnabled();

    private final Arc progressArc = new Arc(CENTER_X, CENTER_Y, RING_RADIUS, RING_RADIUS, 90, 0);
    private final Label percentLabel = Ui.label("0%", "hero-percent");

    public LoginHero() {
        stage.setMinSize(WIDTH, HEIGHT);
        stage.setPrefSize(WIDTH, HEIGHT);
        stage.setMaxSize(WIDTH, HEIGHT);
        getChildren().add(stage);
        setMinSize(0, 0);
        setMouseTransparent(true);

        widthProperty().addListener((obs, oldValue, newValue) -> rescale());
        heightProperty().addListener((obs, oldValue, newValue) -> rescale());
        // Endless animations must not keep running after the login screen is gone.
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                animations.forEach(Animation::stop);
            }
        });

        stage.getChildren().addAll(ring(), ringCenter());
        Node debtCard = debtCard();
        Node paymentCard = paymentCard();
        Node overdueChip = overdueChip();
        Node dueChip = dueChip();
        stage.getChildren().addAll(floating(debtCard, 4, 34, -3, 4.6, 0),
                floating(paymentCard, 318, 232, 2, 4.0, 0.5),
                floating(overdueChip, 352, 40, 0, 3.6, 1.0),
                floating(dueChip, 26, 262, 0, 5.0, 1.5));

        playEntrance(debtCard, paymentCard, overdueChip, dueChip);
    }

    private void rescale() {
        double scale = Math.min(1.15, Math.min(getWidth() / WIDTH, getHeight() / HEIGHT));
        stage.setScaleX(Math.max(scale, 0.1));
        stage.setScaleY(Math.max(scale, 0.1));
    }

    // ----------------------------------------------------------- Ring

    private Node ring() {
        Circle track = new Circle(CENTER_X, CENTER_Y, RING_RADIUS);
        track.setFill(null);
        track.setStroke(theme.ringTrack);
        track.setStrokeWidth(12);

        Circle inner = new Circle(CENTER_X, CENTER_Y, 72);
        inner.setFill(theme.ringInner);
        inner.setStroke(theme.ringTrack);

        progressArc.setType(ArcType.OPEN);
        progressArc.setFill(null);
        progressArc.setStrokeWidth(12);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);
        progressArc.setStroke(theme.ring);
        // Negative length draws clockwise; the label follows the arc while it grows.
        progressArc.lengthProperty().addListener((obs, oldValue, length) ->
                percentLabel.setText(Math.round(-length.doubleValue() / 360 * 100) + "%"));

        return new Group(track, inner, progressArc);
    }

    private Node ringCenter() {
        // Fixed width, so the centered number does not shift while it counts up.
        percentLabel.setMinWidth(120);
        percentLabel.setPrefWidth(120);
        percentLabel.setAlignment(Pos.CENTER);
        Label caption = Ui.label(I18n.t("hero.repaid"), "helper");
        VBox center = new VBox(0, percentLabel, caption);
        center.setAlignment(Pos.CENTER);
        center.setPrefSize(140, 140);
        center.relocate(CENTER_X - 70, CENTER_Y - 70);
        return center;
    }

    // ----------------------------------------------------------- Floating cards

    private Node debtCard() {
        Label title = Ui.label(I18n.t("hero.debtTitle"), "ledger-title");
        Label creditor = Ui.label("Kaspi Bank", "helper");
        HBox header = new HBox(10, Ui.iconTile(Icons.BANK, 34), new VBox(1, title, creditor));
        header.setAlignment(Pos.CENTER_LEFT);

        Label amount = Ui.label("291 600 ₸", "stat-value");
        Region fill = new Region();
        fill.getStyleClass().add("meter-fill");
        fill.setPrefSize(172 * 0.17, 5);
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane meter = new StackPane(fill);
        meter.getStyleClass().add("meter-track");
        meter.setAlignment(Pos.CENTER_LEFT);
        meter.setPrefHeight(5);
        meter.setMaxHeight(5);

        VBox card = new VBox(10, header, amount, meter);
        card.getStyleClass().add("mini-card");
        card.setStyle("-fx-padding: 14;");
        card.setPrefWidth(204);
        return card;
    }

    private Node paymentCard() {
        StackPane tile = Ui.iconTile(Icons.CHECK, 34);
        tile.getChildren().get(0).getStyleClass().add("text-success");
        Label title = Ui.label(I18n.t("hero.paymentDone"), "ledger-title");
        Label amount = Ui.label("+29 200 ₸", "ledger-amount", "text-success");
        HBox card = new HBox(10, tile, new VBox(1, title, amount));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("mini-card");
        card.setStyle("-fx-padding: 12 16 12 12;");
        return card;
    }

    private Node overdueChip() {
        Circle dot = new Circle(4);
        dot.getStyleClass().addAll("status-dot", "danger");
        HBox chip = new HBox(8, dot, Ui.label(I18n.t("hero.overdue"), "helper"));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("chip");
        return chip;
    }

    private Node dueChip() {
        String due = Formats.dayMonth(LocalDate.now().plusDays(21));
        HBox chip = new HBox(8, Icons.create(Icons.CALENDAR, 13), Ui.label(I18n.t("hero.due", due), "helper"));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("chip");
        return chip;
    }

    /**
     * Places the node and makes it slowly bob up and down. The bobbing runs on a wrapper group,
     * so the node itself stays free for the entrance animation. The wrapper is drawn from a
     * cached bitmap, so its text glides smoothly instead of jittering between pixels.
     */
    private Node floating(Node node, double x, double y, double angle, double seconds, double delaySeconds) {
        node.setRotate(angle);
        Group wrapper = new Group(node);
        wrapper.relocate(x, y);
        wrapper.setCache(true);
        wrapper.setCacheHint(CacheHint.QUALITY);
        if (animated) {
            TranslateTransition bob = new TranslateTransition(Duration.seconds(seconds), wrapper);
            bob.setFromY(0);
            bob.setToY(-6);
            bob.setAutoReverse(true);
            bob.setCycleCount(Animation.INDEFINITE);
            bob.setInterpolator(Interpolator.EASE_BOTH);
            bob.setDelay(Duration.seconds(delaySeconds));
            bob.play();
            animations.add(bob);
        }
        return wrapper;
    }

    // ----------------------------------------------------------- Entrance

    private void playEntrance(Node... cards) {
        double finalLength = -360 * REPAID_SHARE;
        if (!animated) {
            progressArc.setLength(finalLength);
            return;
        }

        Timeline fillRing = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressArc.lengthProperty(), 0)),
                new KeyFrame(Duration.seconds(1.8),
                        new KeyValue(progressArc.lengthProperty(), finalLength, Interpolator.SPLINE(0.2, 0.8, 0.2, 1))));
        fillRing.setDelay(Duration.millis(350));
        fillRing.play();
        animations.add(fillRing);

        for (int i = 0; i < cards.length; i++) {
            Node card = cards[i];
            card.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(500), card);
            fade.setToValue(1);
            TranslateTransition rise = new TranslateTransition(Duration.millis(700), card);
            rise.setFromY(20);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1));
            ParallelTransition entrance = new ParallelTransition(fade, rise);
            entrance.setDelay(Duration.millis(600 + i * 160));
            entrance.play();
            animations.add(entrance);
        }
    }
}
