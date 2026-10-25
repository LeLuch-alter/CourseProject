package kz.kvit.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;

/** Small building blocks shared by the screens. */
public final class Ui {

    private Ui() {
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    /** Label whose wrapped text keeps its full height instead of being cut with "...". */
    public static Label wrapped(String text, String... styleClasses) {
        Label label = label(text, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    /** Small all-caps label above a heading. */
    public static Label eyebrow(String text) {
        Label label = label(text.toUpperCase(I18n.locale()), "eyebrow");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    public static StackPane iconTile(String glyph, double size) {
        StackPane tile = new StackPane(Icons.create(glyph, size * 0.42));
        tile.getStyleClass().add("icon-tile");
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        return tile;
    }

    public static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Region hairline() {
        Region line = new Region();
        line.getStyleClass().add("hairline");
        return line;
    }

    public static void tooltip(Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(350));
        Tooltip.install(node, tooltip);
    }

    /** Centered "nothing here" block: icon tile, heading, explanation and an optional action. */
    public static VBox emptyState(String glyph, String title, String text, Node action) {
        Label heading = label(title, "heading-sm");
        Label body = wrapped(text, "body-muted");
        body.setMaxWidth(440);
        body.setStyle("-fx-text-alignment: center;");

        VBox box = new VBox(12, iconTile(glyph, 56), heading, body);
        if (action != null) {
            VBox.setMargin(action, new Insets(8, 0, 0, 0));
            box.getChildren().add(action);
        }
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Text field inside a rounded box with a search icon. */
    public static HBox searchBox(TextField field, String prompt) {
        field.setPromptText(prompt);
        HBox box = new HBox(4, Icons.create(Icons.SEARCH, 13), field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("search-box");
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    /** Pill-shaped group of segments (theme, language). */
    public static HBox segmented(ToggleButton... segments) {
        HBox group = new HBox();
        group.getStyleClass().add("segmented");
        group.setAlignment(Pos.CENTER_LEFT);
        for (ToggleButton segment : segments) {
            segment.getStyleClass().add("segment");
            group.getChildren().add(segment);
        }
        return group;
    }

    /** Form field: label above the control. */
    public static VBox field(String caption, Node control) {
        VBox box = new VBox(6, label(caption, "field-label"), control);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    /** Colored status badge for a debt: overdue, active, paid or cancelled. */
    public static Label statusBadge(DebtOverview overview) {
        if (overview.overdue()) {
            return label(I18n.t("status.OVERDUE"), "badge", "danger");
        }
        DebtStatus status = overview.debt().getStatus();
        String style = switch (status) {
            case ACTIVE -> "info";
            case PAID -> "success";
            case CANCELLED -> "muted";
        };
        return label(status.toString(), "badge", style);
    }

    /** Standard page frame: eyebrow + title on the left, actions on the right, content below. */
    public static VBox page(String eyebrow, String title, Node actions) {
        VBox titles = new VBox(6, eyebrow(eyebrow), label(title, "heading"));
        HBox header = new HBox(16, titles, hSpacer());
        if (actions != null) {
            header.getChildren().add(actions);
        }
        header.setAlignment(Pos.BOTTOM_LEFT);

        VBox page = new VBox(24, header);
        page.setPadding(new Insets(28, 36, 36, 36));
        // Never squeeze the page below its natural height: a scroll bar appears instead.
        page.setMinHeight(Region.USE_PREF_SIZE);
        return page;
    }
}
