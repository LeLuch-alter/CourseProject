package kz.kvit.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kz.kvit.i18n.I18n;

/**
 * Wraps the main window content and shows dialogs and toast messages above it.
 * Dialogs are drawn inside the window (not as separate OS windows), so they always
 * match the current theme. Escape closes the open dialog.
 */
public class ModalHost extends StackPane {
    private final StackPane modalLayer = new StackPane();
    private final VBox toastLayer = new VBox(8);
    private Runnable onModalClosed;

    public ModalHost(Node content) {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("modal-backdrop");
        backdrop.setOnMouseClicked(event -> closeModal());
        modalLayer.getChildren().add(backdrop);
        modalLayer.setVisible(false);

        toastLayer.setAlignment(Pos.BOTTOM_RIGHT);
        toastLayer.setPadding(new Insets(24));
        toastLayer.setPickOnBounds(false);
        toastLayer.setMouseTransparent(true);

        getChildren().addAll(content, modalLayer, toastLayer);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && isModalOpen()) {
                closeModal();
                event.consume();
            }
        });
    }

    public boolean isModalOpen() {
        return modalLayer.isVisible();
    }

    /** Shows the card centered above a dimmed backdrop. onClosed runs when it is closed in any way. */
    public void showModal(Region card, Runnable onClosed) {
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        modalLayer.getChildren().setAll(modalLayer.getChildren().get(0), card);
        modalLayer.setVisible(true);
        onModalClosed = onClosed;
        Motion.fadeIn(modalLayer.getChildren().get(0), 0);
        Motion.fadeIn(card, 40, 10);
    }

    public void closeModal() {
        if (!isModalOpen()) {
            return;
        }
        modalLayer.setVisible(false);
        modalLayer.getChildren().remove(1, modalLayer.getChildren().size());
        if (onModalClosed != null) {
            Runnable callback = onModalClosed;
            onModalClosed = null;
            callback.run();
        }
    }

    /** Small message in the bottom-right corner that disappears by itself. */
    public void toast(String message, boolean error) {
        Label text = Ui.label(message, "ledger-title");
        HBox toast = new HBox(10, Icons.create(error ? Icons.ERROR : Icons.CHECK, 15), text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().add("toast");
        if (error) {
            toast.getStyleClass().add("error");
        }
        toast.setMaxWidth(420);
        toastLayer.getChildren().add(toast);
        Motion.fadeIn(toast, 0, 12);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setToValue(0);
        SequentialTransition life = new SequentialTransition(new PauseTransition(Duration.seconds(3.2)), fadeOut);
        life.setOnFinished(event -> toastLayer.getChildren().remove(toast));
        life.play();
    }

    /** Standard dialog card: title, content, and a row of buttons. */
    public static VBox dialogCard(String title, Node content, Button... buttons) {
        Button close = new Button(null, Icons.create(Icons.CLOSE, 12));
        close.getStyleClass().addAll("quiet", "icon-button");
        Ui.tooltip(close, I18n.t("common.close") + " (Esc)");

        HBox header = new HBox(12, Ui.label(title, "auth-title"), Ui.hSpacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(10, buttons);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(20, header, content, actions);
        card.getStyleClass().add("modal-card");
        card.setPadding(new Insets(26, 28, 24, 28));
        card.setPrefWidth(480);
        card.setUserData(close);
        return card;
    }

    /** Wires the close button of a card created by dialogCard. */
    public void showDialog(VBox card, Runnable onClosed) {
        if (card.getUserData() instanceof Button close) {
            close.setOnAction(event -> closeModal());
        }
        showModal(card, onClosed);
    }

    /** Yes/no question; onConfirm runs only after the user agrees. */
    public void confirm(String title, String message, String confirmText, boolean dangerous, Runnable onConfirm) {
        Button cancel = new Button(I18n.t("common.cancel"));
        cancel.setCancelButton(true);
        Button confirm = new Button(confirmText);
        confirm.getStyleClass().add(dangerous ? "danger-button" : "cta");
        confirm.setDefaultButton(true);

        VBox card = dialogCard(title, Ui.wrapped(message, "body-muted"), cancel, confirm);
        card.setPrefWidth(420);
        cancel.setOnAction(event -> closeModal());
        confirm.setOnAction(event -> {
            closeModal();
            onConfirm.run();
        });
        showDialog(card, null);
        confirm.requestFocus();
    }
}
