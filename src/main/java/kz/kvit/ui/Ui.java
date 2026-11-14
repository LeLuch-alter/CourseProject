package kz.kvit.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.model.DebtOverview;
import kz.kvit.model.DebtStatus;

import java.util.Locale;

/** Small building blocks shared by the screens. The look comes from styles.css. */
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

    /** Small all-caps monospace label above a heading or a section. */
    public static Label eyebrow(String text) {
        Label label = label(text.toUpperCase(I18n.locale()), "eyebrow");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    /** Square tile with a thin border and an icon in the middle (stat cards, empty states). */
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

    public static Region vSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
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

    /** Card: dark surface, thin border, rounded corners, 20px padding. */
    public static VBox card(double spacing, Node... children) {
        VBox card = new VBox(spacing, children);
        card.getStyleClass().add("glass-panel");
        card.setPadding(new Insets(20));
        return card;
    }

    /** Main action of a screen: white pill button (dark pill in the light theme). */
    public static Button primaryButton(String text, String glyph) {
        Button button = button(text, glyph);
        button.getStyleClass().add("primary");
        return button;
    }

    /** Ordinary outlined button with an optional icon (glyph may be null). */
    public static Button button(String text, String glyph) {
        return new Button(text, glyph == null ? null : Icons.create(glyph, 13));
    }

    /** Borderless button with only an icon; the text is shown as a tooltip. */
    public static Button iconButton(String glyph, String tooltipText) {
        Button button = new Button(null, Icons.create(glyph, 15));
        button.getStyleClass().addAll("quiet", "icon-button");
        if (tooltipText != null) {
            tooltip(button, tooltipText);
        }
        return button;
    }

    /**
     * One icon button that cycles the theme: system -> light -> dark -> system.
     * The choice is saved and onChanged rebuilds the screen with the new stylesheet.
     */
    public static Button themeToggle(UserSettings settings, Runnable onChanged) {
        UserSettings.ThemeChoice choice = settings.getTheme();
        String glyph = switch (choice) {
            case SYSTEM -> Icons.SYSTEM;
            case LIGHT -> Icons.SUN;
            case DARK -> Icons.MOON;
        };
        Button button = iconButton(glyph,
                I18n.t("settings.theme") + ": " + I18n.t("settings.theme." + choice.name().toLowerCase(Locale.ROOT)));
        button.setOnAction(event -> {
            UserSettings.ThemeChoice next = switch (choice) {
                case SYSTEM -> UserSettings.ThemeChoice.LIGHT;
                case LIGHT -> UserSettings.ThemeChoice.DARK;
                case DARK -> UserSettings.ThemeChoice.SYSTEM;
            };
            settings.setTheme(next);
            onChanged.run();
        });
        return button;
    }

    /** Centered "nothing here" block: icon tile, heading, explanation and an optional action. */
    public static VBox emptyState(String glyph, String title, String text, Node action) {
        Label heading = label(title, "modal-title");
        Label body = wrapped(text, "body-muted");
        body.setMaxWidth(420);
        body.setStyle("-fx-text-alignment: center;");

        VBox box = new VBox(10, iconTile(glyph, 56), heading, body);
        VBox.setMargin(heading, new Insets(6, 0, 0, 0));
        if (action != null) {
            VBox.setMargin(action, new Insets(12, 0, 0, 0));
            box.getChildren().add(action);
        }
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }

    /** Text field inside a rounded box with a search icon. */
    public static HBox searchBox(TextField field, String prompt) {
        field.setPromptText(prompt);
        HBox box = new HBox(4, Icons.create(Icons.SEARCH, 14), field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("search-box");
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    /** Bordered group of segments (theme, language, periods); the selected one is filled. */
    public static HBox segmented(ToggleButton... segments) {
        HBox group = new HBox();
        group.getStyleClass().add("segmented");
        group.setAlignment(Pos.CENTER_LEFT);
        group.setMaxWidth(Region.USE_PREF_SIZE);
        for (ToggleButton segment : segments) {
            segment.getStyleClass().add("segment");
            group.getChildren().add(segment);
        }
        return group;
    }

    /** Form field: small caps caption above the control. */
    public static VBox field(String caption, Node control) {
        VBox box = new VBox(6, label(caption.toUpperCase(I18n.locale()), "field-label"), control);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    /**
     * Chip with a colored dot and an all-caps mono text, like the web StatusChip.
     * tone: "danger", "success", "active", "warn" or "muted".
     */
    public static Label statusChip(String text, String tone) {
        Circle dot = new Circle(3);
        dot.getStyleClass().addAll("status-dot", tone);
        Label chip = label(text.toUpperCase(I18n.locale()), "badge", "status-chip");
        chip.setGraphic(dot);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** Status chip for a debt: overdue, active, paid or cancelled. */
    public static Label statusBadge(DebtOverview overview) {
        if (overview.overdue()) {
            return statusChip(I18n.t("status.OVERDUE"), "danger");
        }
        DebtStatus status = overview.debt().getStatus();
        String tone = switch (status) {
            case ACTIVE -> "active";
            case PAID -> "success";
            case CANCELLED -> "muted";
        };
        return statusChip(status.toString(), tone);
    }

    /** Keyboard shortcut hint, e.g. "Ctrl+F". */
    public static Label kbd(String keys) {
        Label label = label(keys, "kbd");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    /** Round avatar with up to two initials taken from a name or an email address. */
    public static StackPane avatar(String nameOrEmail, double size) {
        Label letters = label(initials(nameOrEmail));
        letters.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px;");
        StackPane avatar = new StackPane(letters);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    /** "Daniyar Akhmetov" -> "DA", "daniyar@kvit.demo" -> "DA", "" -> "?". */
    static String initials(String nameOrEmail) {
        if (nameOrEmail == null || nameOrEmail.isBlank()) {
            return "?";
        }
        String name = nameOrEmail.contains("@") ? nameOrEmail.substring(0, nameOrEmail.indexOf('@')) : nameOrEmail;
        String[] words = name.trim().split("[\\s._-]+");
        String result;
        if (words.length >= 2 && !words[0].isEmpty() && !words[1].isEmpty()) {
            result = "" + words[0].charAt(0) + words[1].charAt(0);
        } else {
            result = name.trim().length() >= 2 ? name.trim().substring(0, 2) : name.trim();
        }
        return result.isEmpty() ? "?" : result.toUpperCase(I18n.locale());
    }

    /** Standard page frame: eyebrow + title on the left, actions on the right, content below. */
    public static VBox page(String eyebrow, String title, Node actions) {
        VBox titles = new VBox(4, eyebrow(eyebrow), label(title, "heading"));
        HBox header = new HBox(16, titles, hSpacer());
        if (actions != null) {
            header.getChildren().add(actions);
        }
        header.setAlignment(Pos.BOTTOM_LEFT);

        VBox page = new VBox(20, header);
        page.setPadding(new Insets(24, 28, 32, 28));
        // Never squeeze the page below its natural height: a scroll bar appears instead.
        page.setMinHeight(Region.USE_PREF_SIZE);
        return page;
    }
}
