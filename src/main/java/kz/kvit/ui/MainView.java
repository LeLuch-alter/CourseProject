package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.service.AuthService;
import kz.kvit.service.DebtService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Main window: a top command bar with the sections and the selected section below it.
 * Shortcuts: Ctrl+1..6 sections, Ctrl+N new record, Ctrl+F search, F5 refresh, Esc closes a dialog.
 * For safety the user is signed out automatically after IDLE_MINUTES without any input.
 */
public class MainView {
    private static final int DEBTS_SECTION = 1;
    private static final long IDLE_MINUTES = 15;

    private final AuthService authService;
    private final DebtService debtService;
    private final UserSettings settings;
    private final Consumer<String> onSignedOut;
    private final Runnable onSettingsChanged;

    private final BorderPane frame = new BorderPane();
    private final StackPane pageHolder = new StackPane();
    private final ModalHost modalHost;
    private final ToggleGroup navigation = new ToggleGroup();
    private final List<ToggleButton> navItems = new ArrayList<>();
    private Page currentPage;
    private int selectedSection;
    private long lastActivity = System.currentTimeMillis();
    private Timeline idleCheck;

    public MainView(AuthService authService, DebtService debtService, UserSettings settings, int initialSection,
                    Consumer<String> onSignedOut, Runnable onSettingsChanged) {
        this.authService = authService;
        this.debtService = debtService;
        this.settings = settings;
        this.onSignedOut = onSignedOut;
        this.onSettingsChanged = onSettingsChanged;
        this.modalHost = new ModalHost(frame);
        build();
        startIdleWatch();
        navItems.get(initialSection).fire();
    }

    public Parent getRoot() {
        return modalHost;
    }

    public int getSelectedSection() {
        return selectedSection;
    }

    public void openSection(int index) {
        navItems.get(index).fire();
    }

    /** Opens the Debts section with a filter already applied (e.g. only overdue debts). */
    private void openDebts(DebtsView.Filter filter) {
        navItems.get(DEBTS_SECTION).setSelected(true);
        selectedSection = DEBTS_SECTION;
        showPage(new DebtsView(debtService, modalHost, filter));
    }

    private void build() {
        navItem(Icons.HOME, "nav.dashboard", () -> new DashboardView(debtService, modalHost, this::openSection,
                () -> openDebts(DebtsView.Filter.OVERDUE)));
        navItem(Icons.LIST, "nav.debts", () -> new DebtsView(debtService, modalHost, DebtsView.Filter.ALL));
        navItem(Icons.PAYMENT, "nav.payments", () -> new PaymentsView(debtService, modalHost));
        navItem(Icons.CHART, "nav.reports", () -> new ReportsView(debtService));
        navItem(Icons.CALENDAR, "nav.calendar", () -> new CalendarView(debtService));
        navItem(Icons.SETTINGS, "nav.settings", () -> new SettingsView(authService, settings, onSettingsChanged));

        frame.setTop(buildTopBar());
        frame.setCenter(new StackPane(new BlueprintBackground(), pageHolder));
        modalHost.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    private HBox buildTopBar() {
        HBox nav = new HBox(4);
        nav.getChildren().addAll(navItems);
        nav.setAlignment(Pos.CENTER_LEFT);

        Label email = Ui.label(authService.getCurrentUserEmail(), "helper");
        email.setMaxWidth(180);
        // On narrow windows only the icon is shown, the email is in the tooltip.
        email.visibleProperty().bind(frame.widthProperty().greaterThan(1200));
        email.managedProperty().bind(email.visibleProperty());
        HBox userChip = new HBox(8, Icons.create(Icons.CONTACT, 14), email);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");
        Ui.tooltip(userChip, authService.getCurrentUserEmail());

        Button signOut = new Button(null, Icons.create(Icons.SIGN_OUT, 14));
        signOut.getStyleClass().addAll("quiet", "icon-button");
        Ui.tooltip(signOut, I18n.t("nav.signOut"));
        signOut.setOnAction(event -> signOut(signOut));

        HBox brand = Brand.lockup(30);
        brand.setPadding(new Insets(0, 20, 0, 0));
        HBox bar = new HBox(8, brand, nav, Ui.hSpacer(), userChip, signOut);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private void navItem(String glyph, String textKey, Supplier<Page> pageFactory) {
        int index = navItems.size();
        ToggleButton item = new ToggleButton(I18n.t(textKey), Icons.create(glyph, 14));
        item.setGraphicTextGap(8);
        item.setToggleGroup(navigation);
        item.getStyleClass().add("nav-item");
        Ui.tooltip(item, I18n.t(textKey) + "   Ctrl+" + (index + 1));
        item.setOnAction(event -> {
            // Keep one section always selected, even if the active item is clicked again.
            item.setSelected(true);
            selectedSection = index;
            showPage(pageFactory.get());
        });
        navItems.add(item);
    }

    private void showPage(Page page) {
        currentPage = page;
        ScrollPane scroll = new ScrollPane(page.getRoot());
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageHolder.getChildren().setAll(scroll);
        Motion.fadeIn(page.getRoot(), 0);
    }

    private void handleShortcut(KeyEvent event) {
        if (modalHost.isModalOpen() || currentPage == null) {
            return;
        }
        if (event.getCode() == KeyCode.F5) {
            currentPage.refresh();
            event.consume();
            return;
        }
        if (!event.isShortcutDown()) {
            return;
        }
        switch (event.getCode()) {
            case DIGIT1, NUMPAD1 -> openSection(0);
            case DIGIT2, NUMPAD2 -> openSection(1);
            case DIGIT3, NUMPAD3 -> openSection(2);
            case DIGIT4, NUMPAD4 -> openSection(3);
            case DIGIT5, NUMPAD5 -> openSection(4);
            case DIGIT6, NUMPAD6 -> openSection(5);
            case N -> currentPage.createNew();
            case F -> currentPage.focusSearch();
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void signOut(Button button) {
        button.setDisable(true);
        BackgroundTask.run(
                () -> {
                    authService.signOut();
                    return null;
                },
                result -> onSignedOut.accept(null),
                message -> {
                    // signOut() clears the local session even if the server call fails.
                    System.err.println("Sign out: " + message);
                    onSignedOut.accept(null);
                });
    }

    private void startIdleWatch() {
        modalHost.addEventFilter(InputEvent.ANY, event -> lastActivity = System.currentTimeMillis());
        idleCheck = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            if (System.currentTimeMillis() - lastActivity > IDLE_MINUTES * 60_000) {
                idleCheck.stop();
                String message = I18n.t("login.idleSignedOut", IDLE_MINUTES);
                BackgroundTask.run(() -> {
                            authService.signOut();
                            return null;
                        },
                        result -> onSignedOut.accept(message),
                        error -> onSignedOut.accept(message));
            }
        }));
        idleCheck.setCycleCount(Animation.INDEFINITE);
        idleCheck.play();
        // Stop watching once this window content is replaced (sign out, theme or language change).
        modalHost.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                idleCheck.stop();
            }
        });
    }
}
