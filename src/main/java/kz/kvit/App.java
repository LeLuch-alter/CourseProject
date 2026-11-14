package kz.kvit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import kz.kvit.config.AppConfig;
import kz.kvit.config.UserSettings;
import kz.kvit.i18n.I18n;
import kz.kvit.repository.DebtRepository;
import kz.kvit.repository.InstallmentRepository;
import kz.kvit.repository.LoanRepository;
import kz.kvit.repository.NotificationRepository;
import kz.kvit.repository.PaymentRepository;
import kz.kvit.repository.ProfileRepository;
import kz.kvit.repository.SupabaseClient;
import kz.kvit.service.AuthService;
import kz.kvit.service.DebtService;
import kz.kvit.service.LoanService;
import kz.kvit.service.NotificationService;
import kz.kvit.service.ProfileService;
import kz.kvit.ui.Brand;
import kz.kvit.ui.LoginView;
import kz.kvit.ui.MainView;
import kz.kvit.ui.Theme;
import kz.kvit.ui.WindowsIntegration;

/**
 * Entry point: creates the layers once and switches between the login and the main screen.
 * When the theme or language changes, the current screen is simply built again.
 */
public class App extends Application {
    private final UserSettings settings = new UserSettings();
    private Stage stage;
    private AuthService authService;
    private DebtService debtService;
    // Services for the newer screens (settings reminders, notifications, loans); see MainView wiring.
    private ProfileService profileService;
    private NotificationService notificationService;
    private LoanService loanService;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        applySettings();

        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (IllegalStateException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
            alert.setHeaderText(I18n.t("config.errorTitle"));
            alert.showAndWait();
            Platform.exit();
            return;
        }

        SupabaseClient client = new SupabaseClient(config);
        authService = new AuthService(client);
        debtService = new DebtService(new DebtRepository(client), new PaymentRepository(client),
                new InstallmentRepository(client));
        ProfileRepository profileRepository = new ProfileRepository(client);
        profileService = new ProfileService(profileRepository, client);
        notificationService = new NotificationService(new NotificationRepository(client));
        loanService = new LoanService(new LoanRepository(client), profileRepository, client);

        stage.setTitle(Brand.NAME);
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        showLogin();
        stage.getIcons().add(Brand.windowIcon(stage.getScene()));
        stage.show();
        WindowsIntegration.applyTitleBar(stage, Theme.current().isDark());

        // "System" theme follows the Windows setting while the app is running.
        Platform.getPreferences().colorSchemeProperty().addListener((obs, oldScheme, newScheme) -> {
            if (settings.getTheme() == UserSettings.ThemeChoice.SYSTEM) {
                reloadUi();
            }
        });
    }

    private void applySettings() {
        I18n.setLanguage(settings.getLanguage());
        Theme.setCurrent(Theme.resolve(settings.getTheme()));
    }

    /** Called after the theme or language was changed: rebuild the visible screen. */
    private void reloadUi() {
        applySettings();
        if (mainView != null) {
            showMain(mainView.getSelectedSection());
        } else {
            showLogin();
        }
        WindowsIntegration.applyTitleBar(stage, Theme.current().isDark());
    }

    private void showLogin() {
        showLogin(null);
    }

    /** @param message optional notice shown on the login card, e.g. after an automatic sign-out */
    private void showLogin(String message) {
        mainView = null;
        setContent(new LoginView(authService, settings, message, () -> showMain(0), this::reloadUi).getRoot());
    }

    private void showMain(int section) {
        mainView = new MainView(authService, debtService, profileService, notificationService, loanService, settings, section,
                this::showLogin, this::reloadUi);
        setContent(mainView.getRoot());
    }

    private void setContent(Parent content) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(content, 1240, 780, Theme.current().canvas);
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(content);
            stage.getScene().setFill(Theme.current().canvas);
        }
        applyThemeStylesheets(stage.getScene());
    }

    /** styles.css always; theme-light.css on top of it redefines the color tokens for the light theme. */
    private void applyThemeStylesheets(Scene scene) {
        scene.getStylesheets().setAll(getClass().getResource("ui/styles.css").toExternalForm());
        if (!Theme.current().isDark()) {
            scene.getStylesheets().add(getClass().getResource("ui/theme-light.css").toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
