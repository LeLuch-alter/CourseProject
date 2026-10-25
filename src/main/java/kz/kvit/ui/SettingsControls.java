package kz.kvit.ui;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;

/**
 * Theme and language switches. Used on the login screen and in Settings.
 * After a change the settings are saved and onChanged rebuilds the screen.
 */
public final class SettingsControls {

    private SettingsControls() {
    }

    public static HBox languageSwitch(UserSettings settings, Runnable onChanged) {
        ToggleGroup group = new ToggleGroup();
        HBox segmented = Ui.segmented();
        for (I18n.Language language : I18n.Language.values()) {
            ToggleButton segment = new ToggleButton(language.getShortName());
            segment.setToggleGroup(group);
            segment.setSelected(language == I18n.getLanguage());
            Ui.tooltip(segment, language.getNativeName());
            segment.setOnAction(event -> {
                segment.setSelected(true);
                if (language != I18n.getLanguage()) {
                    settings.setLanguage(language);
                    onChanged.run();
                }
            });
            segment.getStyleClass().add("segment");
            segmented.getChildren().add(segment);
        }
        return segmented;
    }

    /** Icons only (compact): system / light / dark. */
    public static HBox themeSwitch(UserSettings settings, Runnable onChanged, boolean withText) {
        ToggleGroup group = new ToggleGroup();
        HBox segmented = Ui.segmented();
        addThemeSegment(segmented, group, settings, onChanged, UserSettings.ThemeChoice.SYSTEM,
                Icons.SYSTEM, "settings.theme.system", withText);
        addThemeSegment(segmented, group, settings, onChanged, UserSettings.ThemeChoice.LIGHT,
                Icons.SUN, "settings.theme.light", withText);
        addThemeSegment(segmented, group, settings, onChanged, UserSettings.ThemeChoice.DARK,
                Icons.MOON, "settings.theme.dark", withText);
        return segmented;
    }

    private static void addThemeSegment(HBox segmented, ToggleGroup group, UserSettings settings,
                                        Runnable onChanged, UserSettings.ThemeChoice choice,
                                        String glyph, String textKey, boolean withText) {
        ToggleButton segment = new ToggleButton(withText ? I18n.t(textKey) : null, Icons.create(glyph, 12));
        segment.setGraphicTextGap(6);
        segment.setToggleGroup(group);
        segment.setSelected(settings.getTheme() == choice);
        Ui.tooltip(segment, I18n.t(textKey));
        segment.setOnAction(event -> {
            segment.setSelected(true);
            if (settings.getTheme() != choice) {
                settings.setTheme(choice);
                onChanged.run();
            }
        });
        segment.getStyleClass().add("segment");
        segmented.getChildren().add(segment);
    }
}
