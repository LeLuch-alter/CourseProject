package kz.kvit.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/** Quiet background of every screen: a faint semi-transparent grid that fades out towards the edges. */
public class BlueprintBackground extends Pane {
    private static final double CELL = 64;

    private final Theme theme = Theme.current();
    private final Canvas grid = new Canvas();

    public BlueprintBackground() {
        getChildren().add(grid);
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
        grid.setWidth(width);
        grid.setHeight(height);
        GraphicsContext g = grid.getGraphicsContext2D();
        g.setFill(theme.canvas);
        g.fillRect(0, 0, width, height);

        g.setStroke(theme.gridLine);
        g.setLineWidth(1);
        for (double x = CELL; x < width; x += CELL) {
            g.strokeLine(Math.floor(x) + 0.5, 0, Math.floor(x) + 0.5, height);
        }
        for (double y = CELL; y < height; y += CELL) {
            g.strokeLine(0, Math.floor(y) + 0.5, width, Math.floor(y) + 0.5);
        }

        // Fade the grid into the plain background towards the edges.
        g.setFill(new RadialGradient(0, 0, 0.35, 0.5, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(1, theme.canvas)));
        g.fillRect(0, 0, width, height);
    }
}
