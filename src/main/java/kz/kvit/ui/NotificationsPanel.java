package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kz.kvit.i18n.I18n;
import kz.kvit.model.Notification;
import kz.kvit.service.NotificationService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Bell button for the top bar with the number of unread notifications. Clicking it opens the
 * latest notifications in a dialog and marks them as read, like the bell on the web site.
 * The count is refreshed every POLL_SECONDS while the button is on screen.
 */
public class NotificationsPanel {
    private static final int POLL_SECONDS = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm");

    private final NotificationService service;
    private final ModalHost host;
    private final Button bell = new Button(null, Icons.create(Icons.BELL, 15));
    private final Label count = Ui.label("", "badge", "danger", "notification-count");
    private final StackPane root = new StackPane(bell, count);
    private final Timeline poll;
    private boolean loading;

    public NotificationsPanel(NotificationService service, ModalHost host) {
        this.service = service;
        this.host = host;

        bell.getStyleClass().addAll("quiet", "icon-button");
        Ui.tooltip(bell, I18n.t("notify.title"));
        bell.setOnAction(event -> open());
        count.setMouseTransparent(true);
        count.setVisible(false);
        StackPane.setAlignment(count, Pos.TOP_RIGHT);
        root.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);

        poll = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), event -> refresh()));
        poll.setCycleCount(Animation.INDEFINITE);
        // Poll only while the button is shown; stop when the main window content is replaced.
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                poll.stop();
            } else {
                poll.play();
            }
        });
        refresh();
    }

    /** The node to put into the top bar. */
    public Node getRoot() {
        return root;
    }

    /** Reloads the unread count. Errors are not shown as toasts: the next poll simply tries again. */
    public void refresh() {
        if (loading) {
            return;
        }
        loading = true;
        BackgroundTask.run(service::getLatest,
                notifications -> {
                    loading = false;
                    showCount(service.unreadCount(notifications));
                },
                message -> {
                    loading = false;
                    System.err.println("Notifications: " + message);
                    Ui.tooltip(bell, I18n.t("notify.title") + "\n" + message);
                });
    }

    private void showCount(int unread) {
        count.setText(unread > 99 ? "99+" : String.valueOf(unread));
        count.setVisible(unread > 0);
    }

    private void open() {
        VBox list = new VBox(2);
        list.getChildren().add(Ui.label(I18n.t("common.loading"), "helper"));
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button close = new Button(I18n.t("common.close"));
        close.setCancelButton(true);
        close.setOnAction(event -> host.closeModal());
        VBox card = ModalHost.dialogCard(I18n.t("notify.title"), scroll, close);
        card.setPrefWidth(460);
        host.showDialog(card, null);

        BackgroundTask.run(service::getLatest,
                notifications -> {
                    fillList(list, notifications);
                    markAllRead(notifications);
                },
                message -> list.getChildren().setAll(Ui.wrapped(I18n.t("common.loadFailed", message), "helper", "text-danger")));
    }

    private void fillList(VBox list, List<Notification> notifications) {
        list.getChildren().clear();
        if (notifications.isEmpty()) {
            list.getChildren().add(Ui.emptyState(Icons.BELL, I18n.t("notify.empty.title"), I18n.t("notify.empty.text"), null));
            return;
        }
        for (Notification notification : notifications) {
            list.getChildren().add(row(notification));
        }
    }

    private Node row(Notification notification) {
        Label title = Ui.wrapped(notification.title(), "ledger-title");
        Label text = Ui.wrapped(service.describe(notification, Formats::money), "body-muted");
        Label when = Ui.label(TIME.format(notification.createdAt().atZoneSameInstant(ZoneId.systemDefault())), "helper");
        VBox column = new VBox(2, title, text, when);
        HBox row = new HBox(10, column);
        if (!notification.read()) {
            Label dot = Ui.label(I18n.t("notify.new"), "badge", "info");
            row.getChildren().addAll(Ui.hSpacer(), dot);
            row.getStyleClass().add("unread");
        }
        row.getStyleClass().addAll("ledger-row", "notification-row");
        row.setPadding(new Insets(8, 4, 8, 4));
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private void markAllRead(List<Notification> notifications) {
        if (service.unreadCount(notifications) == 0) {
            return;
        }
        BackgroundTask.run(() -> {
                    service.markAllRead(notifications);
                    return null;
                },
                result -> showCount(0),
                message -> host.toast(message, true));
    }
}
