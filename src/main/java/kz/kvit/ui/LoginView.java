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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.service.AuthService;

import java.time.Year;

/**
 * Login screen, laid out like the web login: logo and settings on top, one centered card
 * (sign in or sign up, switched by the link at the bottom of the card), a starry backdrop behind it.
 */
public class LoginView {
    private final AuthService authService;
    private final UserSettings settings;
    private final Runnable onSignedIn;
    private final Runnable onSettingsChanged;

    private final StackPane root = new StackPane();
    private final VBox card = new VBox();
    private final Label modeLabel = Ui.label("", "eyebrow");
    private final Label modeSubtitle = Ui.wrapped("", "body-muted");
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField visiblePasswordField = new TextField();
    private final ToggleButton showPasswordButton = new ToggleButton(null, Icons.create(Icons.EYE, 14));
    private final Label passwordHint = Ui.wrapped(I18n.t("login.passwordHint"), "helper");
    private final Button submitButton = new Button();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final InfoBar infoBar = new InfoBar();
    private final Label switchQuestion = Ui.label("", "body-muted");
    private final Button switchButton = new Button();
    private boolean signUpMode;

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
        HBox topBar = new HBox(8, Brand.lockup(30), Ui.hSpacer(),
                SettingsControls.languageSwitch(settings, onSettingsChanged),
                Ui.themeToggle(settings, onSettingsChanged));
        topBar.setAlignment(Pos.CENTER_LEFT);

        buildCard();
        StackPane center = new StackPane(card);
        center.setPadding(new Insets(16, 0, 16, 0));

        Label footer = Ui.label("© " + Year.now().getValue() + " " + Brand.NAME.toUpperCase(), "login-footer");

        BorderPane layout = new BorderPane(center);
        layout.setTop(topBar);
        layout.setBottom(footer);
        layout.setPadding(new Insets(16, 40, 20, 40));

        root.getChildren().addAll(new BlueprintBackground(), new LoginHero(), layout);

        Motion.fadeIn(card, 60, 14);
        // With a remembered email the cursor goes straight to the password.
        emailField.setText(settings.getLastEmail());
        Platform.runLater(() -> (emailField.getText().isEmpty() ? emailField : passwordField).requestFocus());
    }

    private void buildCard() {
        Circle dot = new Circle(3);
        dot.getStyleClass().addAll("status-dot", "success");
        Label chipText = Ui.label(I18n.t("ui.login.chip").toUpperCase(I18n.locale()), "eyebrow");
        HBox chip = new HBox(8, dot, chipText);
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().add("chip");
        chip.setMaxWidth(HBox.USE_PREF_SIZE);

        Label headline = Ui.wrapped(I18n.t("login.eyebrow"), "login-headline");
        headline.setAlignment(Pos.CENTER);
        headline.setMaxWidth(Double.MAX_VALUE);

        VBox intro = new VBox(16, chip, headline);
        intro.setAlignment(Pos.CENTER);
        intro.setPadding(new Insets(0, 0, 8, 0));

        emailField.setPromptText("name@mail.kz");
        passwordField.setPromptText(I18n.t("login.passwordPrompt"));

        progress.setPrefSize(16, 16);
        progress.setMaxSize(16, 16);
        submitButton.getStyleClass().add("cta");
        submitButton.setDefaultButton(true);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(event -> submit());

        VBox passwordGroup = new VBox(6,
                Ui.label(I18n.t("login.password").toUpperCase(I18n.locale()), "field-label"),
                passwordRow(), passwordHint);

        switchButton.getStyleClass().add("link");
        switchButton.setOnAction(event -> {
            if (signUpMode) {
                showSignInMode();
            } else {
                showSignUpMode();
            }
        });
        HBox switchRow = new HBox(8, switchQuestion, Ui.hSpacer(), switchButton);
        switchRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                intro, Ui.hairline(),
                new VBox(6, modeLabel, modeSubtitle),
                Ui.field(I18n.t("login.email"), emailField), passwordGroup, infoBar, submitButton,
                Ui.hairline(), switchRow);
        VBox.setMargin(submitButton, new Insets(4, 0, 4, 0));
        card.setSpacing(16);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(28, 32, 22, 32));
        card.setPrefWidth(460);
        card.setMaxWidth(460);
        card.setMinWidth(340);
        card.setMaxHeight(VBox.USE_PREF_SIZE);
    }

    /** Password field with an eye button inside it that shows the typed password as plain text. */
    private StackPane passwordRow() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.setPromptText(passwordField.getPromptText());
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setOnAction(event -> submit());

        showPasswordButton.getStyleClass().addAll("quiet", "icon-button");
        showPasswordButton.setFocusTraversable(false);
        Ui.tooltip(showPasswordButton, I18n.t("login.showPassword"));
        showPasswordButton.selectedProperty().addListener((obs, wasShown, show) -> {
            visiblePasswordField.setVisible(show);
            passwordField.setVisible(!show);
            ((Label) showPasswordButton.getGraphic()).setText(show ? Icons.EYE_OFF : Icons.EYE);
            TextField active = show ? visiblePasswordField : passwordField;
            active.requestFocus();
            active.end();
        });

        StackPane row = new StackPane(passwordField, visiblePasswordField, showPasswordButton);
        row.getStyleClass().add("password-row");
        StackPane.setAlignment(showPasswordButton, Pos.CENTER_RIGHT);
        StackPane.setMargin(showPasswordButton, new Insets(0, 4, 0, 0));
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    private void showSignInMode() {
        signUpMode = false;
        modeLabel.setText(I18n.t("login.tab.signIn").toUpperCase(I18n.locale()));
        modeSubtitle.setText(I18n.t("login.signIn.subtitle"));
        submitButton.setText(I18n.t("login.signIn.button"));
        switchQuestion.setText(I18n.t("ui.login.noAccount"));
        switchButton.setText(I18n.t("login.signUp.button"));
        passwordHint.setVisible(false);
        passwordHint.setManaged(false);
        infoBar.hide();
    }

    private void showSignUpMode() {
        signUpMode = true;
        modeLabel.setText(I18n.t("login.tab.signUp").toUpperCase(I18n.locale()));
        modeSubtitle.setText(I18n.t("login.signUp.subtitle"));
        submitButton.setText(I18n.t("login.signUp.button"));
        switchQuestion.setText(I18n.t("ui.login.haveAccount"));
        switchButton.setText(I18n.t("login.signIn.button"));
        passwordHint.setVisible(true);
        passwordHint.setManaged(true);
        infoBar.hide();
    }

    private void submit() {
        if (signUpMode) {
            signUp();
        } else {
            signIn();
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
                        showSignInMode();
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
        switchButton.setDisable(busy);
        submitButton.setDisable(busy);
        if (busy) {
            infoBar.hide();
            submitButton.setText(busyText);
            submitButton.setGraphic(progress);
        } else {
            submitButton.setGraphic(null);
            submitButton.setText(I18n.t(signUpMode ? "login.signUp.button" : "login.signIn.button"));
        }
    }

    private void showError(String message) {
        setBusy(false, null);
        infoBar.show(InfoBar.Severity.ERROR, message);
        Motion.shake(card);
        (showPasswordButton.isSelected() ? visiblePasswordField : passwordField).requestFocus();
    }
}
