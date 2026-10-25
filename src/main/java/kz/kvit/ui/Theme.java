package kz.kvit.ui;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import kz.kvit.config.UserSettings;

/**
 * The two color themes. Most colors live in styles.css as tokens; the values here are only
 * for things drawn in Java code (background grid, login hero), which CSS cannot reach.
 */
public enum Theme {
    DARK(Color.web("#18181a"), Color.rgb(255, 255, 255, 0.06),
            Color.web("#e4e4e7"), Color.rgb(255, 255, 255, 0.08), Color.web("#202022")),
    LIGHT(Color.web("#f4f4f5"), Color.rgb(0, 0, 0, 0.06),
            Color.web("#3f3f46"), Color.rgb(0, 0, 0, 0.08), Color.web("#ffffff"));

    private static Theme current = DARK;

    public final Color canvas;
    public final Color gridLine;
    public final Color ring;
    public final Color ringTrack;
    public final Color ringInner;

    Theme(Color canvas, Color gridLine, Color ring, Color ringTrack, Color ringInner) {
        this.canvas = canvas;
        this.gridLine = gridLine;
        this.ring = ring;
        this.ringTrack = ringTrack;
        this.ringInner = ringInner;
    }

    public static Theme current() {
        return current;
    }

    public static void setCurrent(Theme theme) {
        current = theme;
    }

    /** SYSTEM follows the Windows setting "Choose your app mode". */
    public static Theme resolve(UserSettings.ThemeChoice choice) {
        return switch (choice) {
            case DARK -> DARK;
            case LIGHT -> LIGHT;
            case SYSTEM -> Platform.getPreferences().getColorScheme() == ColorScheme.LIGHT ? LIGHT : DARK;
        };
    }

    public boolean isDark() {
        return this == DARK;
    }
}
