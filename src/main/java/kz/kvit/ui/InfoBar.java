package kz.kvit.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Inline message (like the Fluent InfoBar): error, success or neutral info. Hidden when empty. */
public class InfoBar extends HBox {
    public enum Severity { INFO, SUCCESS, ERROR }

    private final Label icon = Icons.create(Icons.INFO, 16);
    private final Label message = new Label();

    public InfoBar() {
        super(10);
        getStyleClass().add("info-bar");
        setAlignment(Pos.TOP_LEFT);
        message.setWrapText(true);
        message.setMinHeight(Label.USE_PREF_SIZE);
        icon.setMinWidth(Label.USE_PREF_SIZE);
        getChildren().addAll(icon, message);
        hide();
    }

    public void show(Severity severity, String text) {
        getStyleClass().removeAll("error", "success");
        switch (severity) {
            case ERROR -> {
                getStyleClass().add("error");
                icon.setText(Icons.ERROR);
            }
            case SUCCESS -> {
                getStyleClass().add("success");
                icon.setText(Icons.CHECK);
            }
            case INFO -> icon.setText(Icons.INFO);
        }
        message.setText(text);
        setVisible(true);
        setManaged(true);
        Motion.fadeIn(this, 0);
    }

    public void hide() {
        message.setText("");
        setVisible(false);
        setManaged(false);
    }
}
