package kz.kvit.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.service.AuthService;

/** Settings: theme, language, account, keyboard shortcuts and information about the app. */
public class SettingsView implements Page {
    private final VBox root;

    public SettingsView(AuthService authService, UserSettings settings, Runnable onSettingsChanged) {
        root = Ui.page(I18n.t("settings.eyebrow"), I18n.t("settings.title"), null);

        VBox appearance = card(Icons.SUN, I18n.t("settings.appearance"),
                row(I18n.t("settings.theme"), I18n.t("settings.theme.hint"),
                        SettingsControls.themeSwitch(settings, onSettingsChanged, true)),
                Ui.hairline(),
                row(I18n.t("settings.language"), I18n.t("settings.language.hint"),
                        SettingsControls.languageSwitch(settings, onSettingsChanged)));

        VBox account = card(Icons.CONTACT, I18n.t("settings.account"),
                row(I18n.t("login.email"), I18n.t("settings.account.hint"),
                        Ui.label(authService.getCurrentUserEmail(), "detail-value")),
                Ui.hairline(),
                Ui.wrapped(I18n.t("settings.account.security"), "helper"));

        VBox shortcuts = card(Icons.SETTINGS, I18n.t("settings.shortcuts"), shortcutsGrid());

        VBox about = card(Icons.INFO, I18n.t("settings.about"),
                new HBox(14, Brand.mark(44), new VBox(2, Ui.label(Brand.NAME, "brand-name"),
                        Ui.label(I18n.t("settings.about.version", "1.0"), "helper"))),
                Ui.wrapped(I18n.t("settings.about.text"), "body-muted"));

        VBox left = new VBox(20, appearance, account);
        VBox right = new VBox(20, shortcuts, about);
        HBox columns = new HBox(20, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(0);
        right.setPrefWidth(0);
        root.getChildren().add(columns);
    }

    @Override
    public Node getRoot() {
        return root;
    }

    private VBox card(String glyph, String title, Node... children) {
        HBox header = new HBox(12, Ui.iconTile(glyph, 36), Ui.label(title, "heading-sm"));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(16, header);
        card.getChildren().addAll(children);
        card.getStyleClass().add("glass-panel");
        card.setPadding(new Insets(22, 24, 22, 24));
        return card;
    }

    private HBox row(String title, String hint, Node control) {
        VBox text = new VBox(2, Ui.label(title, "ledger-title"), Ui.wrapped(hint, "helper"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private GridPane shortcutsGrid() {
        String[][] shortcuts = {
                {"Ctrl + 1…6", "settings.shortcut.sections"},
                {"Ctrl + N", "settings.shortcut.new"},
                {"Ctrl + F", "settings.shortcut.search"},
                {"F5", "settings.shortcut.refresh"},
                {"Enter", "settings.shortcut.edit"},
                {"Delete", "settings.shortcut.delete"},
                {"Esc", "settings.shortcut.close"},
        };
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        for (int i = 0; i < shortcuts.length; i++) {
            Label key = Ui.label(shortcuts[i][0], "kbd");
            key.setMinWidth(Label.USE_PREF_SIZE);
            grid.add(key, 0, i);
            grid.add(Ui.label(I18n.t(shortcuts[i][1]), "detail-key"), 1, i);
        }
        return grid;
    }
}
