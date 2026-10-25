package kz.kvit.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.service.AuthService;

/**
 * Login screen: brand and settings on top, animated hero on the left,
 * sign-in / sign-up card on the right.
 */
public class LoginView {
    private final AuthService authService;
    private final UserSettings settings;
    private final Runnable onSignedIn;
    private final Runnable onSettingsChanged;

    private final StackPane root = new StackPane();
    private final VBox card = new VBox();
    private final ToggleButton signInTab = new ToggleButton(I18n.t("login.tab.signIn"));
    private final ToggleButton signUpTab = new ToggleButton(I18n.t("login.tab.signUp"));
    private final Label cardTitle = Ui.label("", "auth-title");
    private final Label cardSubtitle = Ui.wrapped("", "body-muted");
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField visiblePasswordField = new TextField();
    private final ToggleButton showPasswordButton = new ToggleButton(null, Icons.create(Icons.EYE, 13));
    private final Label passwordHint = Ui.wrapped(I18n.t("login.passwordHint"), "helper");
    private final Button submitButton = new Button();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final InfoBar infoBar = new InfoBar();

    /** @param notice optional message shown on the card at start (e.g. "signed out after inactivity") */
    public LoginView(AuthService authService, UserSettings settings, String notice, Runnable onSignedIn,
                     Runnable onSettingsChanged) {
        this.authService = authService;
        this.settings = settings;
        this.onSignedIn = onSignedIn;
        this.onSettingsChanged = onSettingsChanged;
        build();
        showSignInMode();
        if (notice != null) {
            infoBar.show(InfoBar.Severity.INFO, notice);
        }
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        HBox topBar = new HBox(12, Brand.mark(44), Ui.hSpacer(),
                SettingsControls.languageSwitch(settings, onSettingsChanged),
                SettingsControls.themeSwitch(settings, onSettingsChanged, false));
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox hero = buildHeroColumn();
        buildCard();
        HBox columns = new HBox(48, hero, card);
        columns.setAlignment(Pos.CENTER);
        HBox.setHgrow(hero, Priority.ALWAYS);

        BorderPane layout = new BorderPane(columns);
        layout.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 8, 0));
        layout.setPadding(new Insets(22, 48, 28, 48));

        root.getChildren().addAll(new BlueprintBackground(), layout);

        Motion.fadeIn(hero, 0);
        Motion.fadeIn(card, 120);
        // With a remembered email the cursor goes straight to the password.
        emailField.setText(settings.getLastEmail());
        Platform.runLater(() -> (emailField.getText().isEmpty() ? emailField : passwordField).requestFocus());
    }

    private VBox buildHeroColumn() {
        Label wordmark = Ui.label(Brand.NAME, "display");
        Label tagline = Ui.wrapped(I18n.t("login.tagline"), "body-muted");
        tagline.setMaxWidth(440);

        LoginHero heroObject = new LoginHero();
        VBox.setVgrow(heroObject, Priority.ALWAYS);

        VBox column = new VBox(12, Ui.eyebrow(I18n.t("login.eyebrow")), wordmark, tagline, heroObject);
        column.setAlignment(Pos.CENTER_LEFT);
        column.setMinWidth(0);
        return column;
    }

    private void buildCard() {
        ToggleGroup modes = new ToggleGroup();
        for (ToggleButton tab : new ToggleButton[]{signInTab, signUpTab}) {
            tab.setToggleGroup(modes);
            tab.getStyleClass().add("mode-tab");
        }
        // Keep one tab always selected, even if the active one is clicked again.
        modes.selectedToggleProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) {
                oldTab.setSelected(true);
            } else if (newTab == signInTab) {
                showSignInMode();
            } else {
                showSignUpMode();
            }
        });
        signInTab.setSelected(true);
        HBox tabs = new HBox(20, signInTab, signUpTab);

        emailField.setPromptText("name@mail.kz");
        passwordField.setPromptText(I18n.t("login.passwordPrompt"));

        progress.setPrefSize(16, 16);
        progress.setMaxSize(16, 16);
        submitButton.getStyleClass().add("cta");
        submitButton.setDefaultButton(true);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(event -> submit());

        VBox passwordGroup = new VBox(6, Ui.label(I18n.t("login.password"), "field-label"), passwordRow(), passwordHint);

        card.getChildren().addAll(
                new VBox(6, cardTitle, cardSubtitle),
                tabs, Ui.field(I18n.t("login.email"), emailField), passwordGroup, infoBar, submitButton);
        VBox.setMargin(submitButton, new Insets(2, 0, 0, 0));
        card.setSpacing(16);
        card.getStyleClass().add("modal-card");
        card.setPadding(new Insets(30, 32, 30, 32));
        card.setPrefWidth(390);
        card.setMinWidth(340);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
    }

    /** Password field with an eye button that shows the typed password as plain text. */
    private HBox passwordRow() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.setPromptText(passwordField.getPromptText());
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setOnAction(event -> submit());

        showPasswordButton.getStyleClass().add("icon-button");
        Ui.tooltip(showPasswordButton, I18n.t("login.showPassword"));
        showPasswordButton.selectedProperty().addListener((obs, wasShown, show) -> {
            visiblePasswordField.setVisible(show);
            passwordField.setVisible(!show);
            ((Label) showPasswordButton.getGraphic()).setText(show ? Icons.EYE_OFF : Icons.EYE);
            TextField active = show ? visiblePasswordField : passwordField;
            active.requestFocus();
            active.end();
        });

        StackPane fields = new StackPane(passwordField, visiblePasswordField);
        HBox row = new HBox(8, fields, showPasswordButton);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fields, Priority.ALWAYS);
        return row;
    }

    private void showSignInMode() {
        cardTitle.setText(I18n.t("login.signIn.title"));
        cardSubtitle.setText(I18n.t("login.signIn.subtitle"));
        submitButton.setText(I18n.t("login.signIn.button"));
        passwordHint.setVisible(false);
        passwordHint.setManaged(false);
        infoBar.hide();
    }

    private void showSignUpMode() {
        cardTitle.setText(I18n.t("login.signUp.title"));
        cardSubtitle.setText(I18n.t("login.signUp.subtitle"));
        submitButton.setText(I18n.t("login.signUp.button"));
        passwordHint.setVisible(true);
        passwordHint.setManaged(true);
        infoBar.hide();
    }

    private void submit() {
        if (signInTab.isSelected()) {
            signIn();
        } else {
            signUp();
        }
    }

    private void signIn() {
        String email = emailField.getText();
        String password = passwordField.getText();
        setBusy(true, I18n.t("login.signIn.busy"));
        BackgroundTask.run(
                () -> {
                    authService.signIn(email, password);
                    return null;
                },
                result -> {
                    settings.setLastEmail(email);
                    // Do not keep the password in the UI longer than needed.
                    passwordField.clear();
                    setBusy(false, null);
                    onSignedIn.run();
                },
                this::showError);
    }

    private void signUp() {
        String email = emailField.getText();
        String password = passwordField.getText();
        setBusy(true, I18n.t("login.signUp.busy"));
        BackgroundTask.run(
                () -> authService.signUp(email, password),
                loggedIn -> {
                    passwordField.clear();
                    setBusy(false, null);
                    if (loggedIn) {
                        onSignedIn.run();
                    } else {
                        signInTab.setSelected(true);
                        infoBar.show(InfoBar.Severity.SUCCESS, I18n.t("login.signUp.checkEmail", email.trim()));
                    }
                },
                this::showError);
    }

    private void setBusy(boolean busy, String busyText) {
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        visiblePasswordField.setDisable(busy);
        showPasswordButton.setDisable(busy);
        signInTab.setDisable(busy);
        signUpTab.setDisable(busy);
        submitButton.setDisable(busy);
        if (busy) {
            infoBar.hide();
            submitButton.setText(busyText);
            submitButton.setGraphic(progress);
        } else {
            submitButton.setGraphic(null);
            submitButton.setText(I18n.t(signInTab.isSelected() ? "login.signIn.button" : "login.signUp.button"));
        }
    }

    private void showError(String message) {
        setBusy(false, null);
        infoBar.show(InfoBar.Severity.ERROR, message);
        Motion.shake(card);
        (showPasswordButton.isSelected() ? visiblePasswordField : passwordField).requestFocus();
    }
}
