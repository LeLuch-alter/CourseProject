package kz.kvit.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.InputStream;

/**
 * App name and logo. To use a custom logo, put a square PNG at
 * src/main/resources/kz/kvit/ui/logo.png — it then replaces the built-in "K" monogram everywhere,
 * including the window icon.
 */
public final class Brand {
    public static final String NAME = "Kvit";
    private static final String LOGO_RESOURCE = "logo.png";

    private static Image logo;
    private static boolean logoChecked;

    private Brand() {
    }

    /** Logo of the given size: the custom PNG if present, otherwise the monogram. */
    public static Node mark(double size) {
        Image image = customLogo();
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        }
        Label letter = new Label("K");
        letter.getStyleClass().add("brand-monogram");
        letter.setStyle("-fx-font-size: " + Math.round(size * 0.52) + "px;");
        StackPane tile = new StackPane(letter);
        tile.getStyleClass().add("brand-mark");
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        return tile;
    }

    /** Logo + "Kvit" side by side, for headers. */
    public static HBox lockup(double markSize) {
        HBox lockup = new HBox(10, mark(markSize), Ui.label(NAME, "brand-name"));
        lockup.setAlignment(Pos.CENTER_LEFT);
        return lockup;
    }

    /** Image for the window title bar and taskbar. */
    public static Image windowIcon(javafx.scene.Scene sceneForStyles) {
        Image image = customLogo();
        if (image != null) {
            return image;
        }
        // Render the monogram once with the app stylesheet applied.
        Node mark = mark(64);
        StackPane holder = new StackPane(mark);
        holder.setStyle("-fx-background-color: transparent;");
        javafx.scene.Scene scratch = new javafx.scene.Scene(holder, Color.TRANSPARENT);
        scratch.getStylesheets().setAll(sceneForStyles.getStylesheets());
        holder.applyCss();
        holder.layout();
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return mark.snapshot(parameters, null);
    }

    private static Image customLogo() {
        if (!logoChecked) {
            logoChecked = true;
            try (InputStream input = Brand.class.getResourceAsStream(LOGO_RESOURCE)) {
                if (input != null) {
                    logo = new Image(input);
                }
            } catch (java.io.IOException e) {
                System.err.println("Cannot read logo.png: " + e.getMessage());
            }
        }
        return logo;
    }
}
