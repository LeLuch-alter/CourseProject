package kz.kvit.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Quiet background of every screen, like the web app: a plain canvas with two "halftone" dot fields
 * in the bottom-left and top-right corners. Dots get smaller and fainter away from the corner.
 * Drawn once per resize on a Canvas, so it costs nothing while the user works.
 */
public class BlueprintBackground extends Pane {
    private static final double SPACING = 16;

    private final Theme theme = Theme.current();
    private final Canvas canvas = new Canvas();

    public BlueprintBackground() {
        getChildren().add(canvas);
        setMouseTransparent(true);
        widthProperty().addListener((obs, oldValue, newValue) -> redraw());
        heightProperty().addListener((obs, oldValue, newValue) -> redraw());
    }

    private void redraw() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(theme.canvas);
        g.fillRect(0, 0, width, height);

        double radius = Math.min(560, Math.min(width, height) * 0.75);
        drawDotField(g, 0, height, radius);
        drawDotField(g, width, 0, radius * 0.6);
    }

    /** Dots on a regular grid inside a circle around (cornerX, cornerY). */
    private void drawDotField(GraphicsContext g, double cornerX, double cornerY, double radius) {
        double maxDot = 2.2;
        // Only visit grid points inside the square around the circle.
        double startX = firstGridPoint(cornerX - radius);
        double startY = firstGridPoint(cornerY - radius);
        for (double x = startX; x < Math.min(getWidth(), cornerX + radius); x += SPACING) {
            for (double y = startY; y < Math.min(getHeight(), cornerY + radius); y += SPACING) {
                double distance = Math.hypot(x - cornerX, y - cornerY);
                if (distance >= radius) {
                    continue;
                }
                double strength = 1 - distance / radius;
                double size = 0.5 + maxDot * strength;
                g.setFill(withAlpha(theme.dot, theme.dot.getOpacity() * strength * strength));
                g.fillOval(x - size / 2, y - size / 2, size, size);
            }
        }
    }

    private static double firstGridPoint(double from) {
        double first = SPACING / 2;
        while (first < from) {
            first += SPACING;
        }
        return first;
    }

    private static Color withAlpha(Color color, double alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(1, alpha)));
    }
}
