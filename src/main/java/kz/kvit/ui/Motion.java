package kz.kvit.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Shared, short animations. Motion budget: entrances and feedback only in the working
 * screens; continuous ambient motion is used on the login hero alone.
 * Everything is switched off when Windows has animations or high contrast turned off.
 */
public final class Motion {
    private static Boolean enabled;

    private Motion() {
    }

    public static boolean isEnabled() {
        if (enabled == null) {
            boolean highContrast = Platform.getPreferences().getBoolean("Windows.SPI.HighContrast").orElse(false);
            enabled = !highContrast && WindowsIntegration.areAnimationsEnabled();
        }
        return enabled;
    }

    /** Plain fade-in. */
    public static void fadeIn(Node node, double delayMillis) {
        fadeIn(node, delayMillis, 0);
    }

    /** Fade in while rising by riseBy pixels. */
    public static void fadeIn(Node node, double delayMillis, double riseBy) {
        if (!isEnabled()) {
            return;
        }
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(280), node);
        fade.setToValue(1);
        ParallelTransition entrance = new ParallelTransition(fade);
        if (riseBy != 0) {
            TranslateTransition rise = new TranslateTransition(Duration.millis(380), node);
            rise.setFromY(riseBy);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1));
            entrance.getChildren().add(rise);
        }
        entrance.setDelay(Duration.millis(delayMillis));
        runCached(node, entrance);
    }

    /** Short horizontal shake to point at a failed action (e.g. wrong password). */
    public static void shake(Node node) {
        if (!isEnabled()) {
            return;
        }
        TranslateTransition shake = new TranslateTransition(Duration.millis(45), node);
        shake.setFromX(0);
        shake.setByX(6);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(event -> node.setTranslateX(0));
        runCached(node, shake);
    }

    /**
     * While a node moves, draw it from a cached bitmap: text then glides smoothly instead of
     * jumping between pixel positions. Afterwards the cache is dropped so the text is crisp again.
     */
    private static void runCached(Node node, javafx.animation.Animation animation) {
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
        var previous = animation.getOnFinished();
        animation.setOnFinished(event -> {
            if (previous != null) {
                previous.handle(event);
            }
            node.setCache(false);
        });
        animation.play();
    }
}
