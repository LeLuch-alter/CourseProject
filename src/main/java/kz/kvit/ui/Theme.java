package kz.kvit.ui;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import kz.kvit.config.UserSettings;

/**
 * The two color themes. Most colors live in styles.css / theme-light.css as tokens; the values here
 * are only for things drawn in Java code (dotted background, login decoration), which CSS cannot reach.
 * They repeat the CSS tokens -bg, -line, -ink and -surface.
 */
public enum Theme {
    DARK(Color.web("#101010"), Color.rgb(255, 255, 255, 0.07),
            Color.web("#f3f3f3"), Color.web("#212121"), Color.web("#080808"), Color.rgb(255, 255, 255, 0.55)),
    LIGHT(Color.web("#f6f6f4"), Color.rgb(0, 0, 0, 0.07),
            Color.web("#101010"), Color.web("#dededc"), Color.web("#ffffff"), Color.rgb(0, 0, 0, 0.35));

    private static Theme current = DARK;

    public final Color canvas;
    public final Color gridLine;
    public final Color ring;
    public final Color ringTrack;
    public final Color ringInner;
    /** Color of the dots in the decorative background (alpha is reduced further per dot). */
    public final Color dot;

    Theme(Color canvas, Color gridLine, Color ring, Color ringTrack, Color ringInner, Color dot) {
        this.canvas = canvas;
        this.gridLine = gridLine;
        this.ring = ring;
        this.ringTrack = ringTrack;
        this.ringInner = ringInner;
        this.dot = dot;
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
