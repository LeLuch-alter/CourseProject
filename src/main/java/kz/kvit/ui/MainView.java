package kz.kvit.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.service.AuthService;
import kz.kvit.service.DebtService;
import kz.kvit.service.LoanService;
import kz.kvit.service.NotificationService;
import kz.kvit.service.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Main window, laid out like the web app: a sidebar with the sections on the left,
 * a top bar (search, language, theme, notifications slot, user menu) and the selected section below it.
 * Shortcuts: Ctrl+1..9 sections, Ctrl+N new record, Ctrl+F search, F5 refresh, Esc closes a dialog.
 * For safety the user is signed out automatically after IDLE_MINUTES without any input.
 */
public class MainView {
    private static final int DEBTS_SECTION = 1;
    private static final long IDLE_MINUTES = 15;
    /** Below this window width the sidebar shows icons only. */
    private static final double COMPACT_WIDTH = 1100;

    private final AuthService authService;
    private final DebtService debtService;
    private final ProfileService profileService;
    private final NotificationService notificationService;
    private final LoanService loanService;
    private final UserSettings settings;
    private final Consumer<String> onSignedOut;
    private final Runnable onSettingsChanged;

    private final BorderPane frame = new BorderPane();
    private final StackPane pageHolder = new StackPane();
    private final ModalHost modalHost;
    private final ToggleGroup navigation = new ToggleGroup();
    private final List<ToggleButton> navItems = new ArrayList<>();
    private final VBox mainNav = new VBox();
    private final VBox footerNav = new VBox();
    private final VBox sidebar = new VBox();
    private final Label brandName = Ui.label(Brand.NAME, "brand-name");
    private final HBox topBarExtra = new HBox(4);
    private Page currentPage;
    private int selectedSection;
    private long lastActivity = System.currentTimeMillis();
    private Timeline idleCheck;

    public MainView(AuthService authService, DebtService debtService, ProfileService profileService,
                    NotificationService notificationService, LoanService loanService, UserSettings settings, int initialSection,
                    Consumer<String> onSignedOut, Runnable onSettingsChanged) {
        this.authService = authService;
        this.debtService = debtService;
        this.profileService = profileService;
        this.notificationService = notificationService;
        this.loanService = loanService;
        this.settings = settings;
        this.onSignedOut = onSignedOut;
        this.onSettingsChanged = onSettingsChanged;
        this.modalHost = new ModalHost(new StackPane(new BlueprintBackground(), frame));
        build();
        setTopBarExtra(new NotificationsPanel(notificationService, modalHost).getRoot());
        startIdleWatch();
        navItems.get(Math.min(initialSection, navItems.size() - 1)).fire();
    }

    public Parent getRoot() {
        return modalHost;
    }

    public int getSelectedSection() {
        return selectedSection;
    }

    public void openSection(int index) {
        if (index >= 0 && index < navItems.size()) {
            navItems.get(index).fire();
        }
    }

    /**
     * Slot in the top bar between the theme button and the user menu, meant for the notifications bell.
     * Pass null to empty the slot.
     */
    public void setTopBarExtra(Node node) {
        if (node == null) {
            topBarExtra.getChildren().clear();
        } else {
            topBarExtra.getChildren().setAll(node);
        }
    }

    public ModalHost getModalHost() {
        return modalHost;
    }

    /** Opens the Debts section with a filter already applied (e.g. only overdue debts). */
    private void openDebts(DebtsView.Filter filter) {
        navItems.get(DEBTS_SECTION).setSelected(true);
        selectedSection = DEBTS_SECTION;
        showPage(new DebtsView(debtService, modalHost, filter));
    }

    // ------------------------------------------------------------------ Layout

    private void build() {
        // The order of the items is the order of the Ctrl+1..9 shortcuts.
        mainNav.getChildren().addAll(
                navItem(Icons.HOME, "nav.dashboard", () -> new DashboardView(debtService, modalHost, this::openSection,
                        () -> openDebts(DebtsView.Filter.OVERDUE))),
                navItem(Icons.LIST, "nav.debts", () -> new DebtsView(debtService, modalHost, DebtsView.Filter.ALL)),
                navItem(Icons.PAYMENT, "nav.payments", () -> new PaymentsView(debtService, modalHost)),
                navItem(Icons.CHART, "nav.reports", () -> new ReportsView(debtService)),
                navItem(Icons.CALENDAR, "nav.calendar", () -> new CalendarView(debtService)),
                navItem(Icons.DOCUMENT, "loans.title", () -> new LoansView(loanService, modalHost)));
        footerNav.getChildren().add(
                navItem(Icons.SETTINGS, "nav.settings", () -> new SettingsView(authService, settings, onSettingsChanged, profileService, modalHost)));

        BorderPane content = new BorderPane(pageHolder);
        content.setTop(buildTopBar());
        frame.setLeft(buildSidebar());
        frame.setCenter(content);

        frame.widthProperty().addListener((obs, oldWidth, width) ->
                setSidebarCompact(width.doubleValue() < COMPACT_WIDTH));
        modalHost.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    private VBox buildSidebar() {
        HBox brand = new HBox(12, Brand.mark(30), brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("sidebar-brand");

        mainNav.getStyleClass().add("sidebar-nav");
        footerNav.getStyleClass().add("sidebar-footer");

        sidebar.getChildren().addAll(brand, mainNav, Ui.vSpacer(), footerNav);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    /** On narrow windows the sidebar keeps only the icons; the names stay in the tooltips. */
    private void setSidebarCompact(boolean compact) {
        if (compact == sidebar.getStyleClass().contains("compact")) {
            return;
        }
        if (compact) {
            sidebar.getStyleClass().add("compact");
        } else {
            sidebar.getStyleClass().remove("compact");
        }
        brandName.setVisible(!compact);
        brandName.setManaged(!compact);
        for (ToggleButton item : navItems) {
            item.setContentDisplay(compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        }
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(8, searchButton(), Ui.hSpacer(),
                SettingsControls.languageSwitch(settings, onSettingsChanged),
                Ui.themeToggle(settings, onSettingsChanged), topBarExtra, userButton());
        topBarExtra.setAlignment(Pos.CENTER);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** Looks like a search field. Searches the current list, or opens Debts if this page has no search. */
    private Button searchButton() {
        Label text = Ui.label(I18n.t("ui.search.placeholder"), "body-muted");
        HBox content = new HBox(10, Icons.create(Icons.SEARCH, 14), text, Ui.hSpacer(), Ui.kbd("Ctrl+F"));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPrefWidth(232);

        Button search = new Button(null, content);
        search.getStyleClass().add("top-search");
        search.setOnAction(event -> focusSearch());
        return search;
    }

    private void focusSearch() {
        if (!(currentPage instanceof DebtsView) && !(currentPage instanceof PaymentsView)) {
            openSection(DEBTS_SECTION);
        }
        Page page = currentPage;
        Platform.runLater(page::focusSearch);
    }

    /** Avatar + email; a click opens a small menu with Settings and Sign out. */
    private Button userButton() {
        String email = authService.getCurrentUserEmail();
        Button button = new Button(email, Ui.avatar(email, 32));
        button.getStyleClass().add("user-button");
        button.setMaxWidth(260);
        // On narrow windows only the avatar is shown, the email is in the tooltip.
        button.contentDisplayProperty().bind(Bindings.when(frame.widthProperty().greaterThan(1200))
                .then(ContentDisplay.LEFT).otherwise(ContentDisplay.GRAPHIC_ONLY));
        Ui.tooltip(button, email);

        Label signedInAs = Ui.label(email, "helper");
        CustomMenuItem header = new CustomMenuItem(signedInAs, false);
        header.setDisable(true);
        MenuItem settingsItem = new MenuItem(I18n.t("nav.settings"), Icons.create(Icons.SETTINGS, 13));
        settingsItem.setOnAction(event -> openSection(navItems.size() - 1));
        MenuItem signOutItem = new MenuItem(I18n.t("nav.signOut"), Icons.create(Icons.SIGN_OUT, 13));
        signOutItem.getStyleClass().add("danger");
        signOutItem.setOnAction(event -> signOut(button));
        ContextMenu menu = new ContextMenu(header, new SeparatorMenuItem(), settingsItem, signOutItem);

        button.setOnAction(event -> menu.show(button, Side.BOTTOM, 0, 6));
        return button;
    }

    private ToggleButton navItem(String glyph, String textKey, Supplier<Page> pageFactory) {
        int index = navItems.size();
        ToggleButton item = new ToggleButton(I18n.t(textKey), Icons.create(glyph, 16));
        item.setToggleGroup(navigation);
        item.getStyleClass().add("nav-item");
        item.setMaxWidth(Double.MAX_VALUE);
        Ui.tooltip(item, I18n.t(textKey) + "   Ctrl+" + (index + 1));
        item.setOnAction(event -> {
            // Keep one section always selected, even if the active item is clicked again.
            item.setSelected(true);
            selectedSection = index;
            showPage(pageFactory.get());
        });
        navItems.add(item);
        return item;
    }

    private void showPage(Page page) {
        currentPage = page;
        ScrollPane scroll = new ScrollPane(page.getRoot());
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageHolder.getChildren().setAll(scroll);
        Motion.fadeIn(page.getRoot(), 0, 8);
    }

    // ------------------------------------------------------------------ Keyboard

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
        int section = sectionForKey(event.getCode());
        if (section >= 0) {
            openSection(section);
        } else if (event.getCode() == KeyCode.N) {
            currentPage.createNew();
        } else if (event.getCode() == KeyCode.F) {
            currentPage.focusSearch();
        } else {
            return;
        }
        event.consume();
    }

    /** Ctrl+1 -> 0, Ctrl+2 -> 1 ...; -1 if the key is not a digit 1-9. */
    private static int sectionForKey(KeyCode code) {
        if (code.isDigitKey() && code != KeyCode.DIGIT0 && code != KeyCode.NUMPAD0) {
            String name = code.getName();
            return Integer.parseInt(name.substring(name.length() - 1)) - 1;
        }
        return -1;
    }

    // ------------------------------------------------------------------ Session

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
