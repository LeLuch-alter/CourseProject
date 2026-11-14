package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decoration behind the login card, like the web login: a quiet star field and two thin
 * "orbits" (bottom-left and top-right) with a small planet that circles very slowly.
 * Purely decorative; it ignores the mouse and stops its animation when the screen is closed.
 */
public class LoginHero extends Pane {
    /** Same seed every time, so the stars do not jump around when the window is resized. */
    private static final long STAR_SEED = 2026;

    private final Theme theme = Theme.current();
    private final Canvas stars = new Canvas();
    private final Group bottomOrbit;
    private final Group topOrbit;
    private final List<Animation> animations = new ArrayList<>();

    public LoginHero() {
        setMouseTransparent(true);
        bottomOrbit = orbit(260, 46, true);
        topOrbit = orbit(110, 20, false);
        getChildren().addAll(stars, bottomOrbit, topOrbit);

        widthProperty().addListener((obs, oldValue, newValue) -> relayout());
        heightProperty().addListener((obs, oldValue, newValue) -> relayout());
        // Endless animations must not keep running after the login screen is gone.
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                animations.forEach(Animation::stop);
            }
        });
    }

    private void relayout() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        drawStars(width, height);
        bottomOrbit.relocate(-120 - bottomOrbit.getLayoutBounds().getMinX(),
                height - 300 - bottomOrbit.getLayoutBounds().getMinY());
        topOrbit.relocate(width - 170 - topOrbit.getLayoutBounds().getMinX(),
                90 - topOrbit.getLayoutBounds().getMinY());
    }

    private void drawStars(double width, double height) {
        stars.setWidth(width);
        stars.setHeight(height);
        GraphicsContext g = stars.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        Random random = new Random(STAR_SEED);
        int count = (int) (width * height / 4500);
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            double size = random.nextDouble() < 0.9 ? 1.2 : 2.2;
            double alpha = 0.15 + random.nextDouble() * 0.5;
            g.setFill(new Color(theme.ring.getRed(), theme.ring.getGreen(), theme.ring.getBlue(),
                    alpha * (theme.isDark() ? 1 : 0.5)));
            g.fillOval(x, y, size, size);
        }
    }

    /** A circle, a tilted ellipse around it and a small planet travelling along the circle. */
    private Group orbit(double radius, double seconds, boolean clockwise) {
        Circle body = new Circle(radius);
        body.setFill(Color.TRANSPARENT);
        body.setStroke(theme.ringTrack);
        body.setStrokeWidth(1.2);

        Circle inner = new Circle(radius * 0.86);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(theme.ringTrack);
        inner.setStrokeWidth(0.8);

        Ellipse ring = new Ellipse(radius * 1.25, radius * 0.32);
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(theme.ringTrack);
        ring.setStrokeWidth(1);
        ring.setRotate(-14);

        double planetRadius = Math.max(3, radius * 0.03);
        Circle planet = new Circle(radius, 0, planetRadius);
        planet.setFill(theme.dot);
        // The invisible circle makes the group's center the orbit center, so the rotation turns around it.
        Group carrier = new Group(new Circle(radius + planetRadius, Color.TRANSPARENT), planet);
        if (Motion.isEnabled()) {
            RotateTransition spin = new RotateTransition(Duration.seconds(seconds), carrier);
            spin.setByAngle(clockwise ? 360 : -360);
            spin.setInterpolator(Interpolator.LINEAR);
            spin.setCycleCount(Animation.INDEFINITE);
            spin.play();
            animations.add(spin);
        }
        return new Group(body, inner, ring, carrier);
    }
}
