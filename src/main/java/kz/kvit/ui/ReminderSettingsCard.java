package kz.kvit.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kz.kvit.i18n.I18n;
import kz.kvit.model.ReminderMode;
import kz.kvit.model.ReminderSettings;
import kz.kvit.model.TelegramStatus;
import kz.kvit.service.ProfileService;
import kz.kvit.service.ValidationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Settings card "Reminders": how many days before a due date to warn, e-mail copy, and when the
 * server sends Telegram / e-mail reminders (time, every day / weekdays / chosen dates).
 * Also shows whether Telegram is connected (connecting is done on the web site).
 * If the database does not have the newer columns yet, the card says so instead of failing.
 */
public class ReminderSettingsCard {
    private final ProfileService profileService;
    private final ModalHost host;

    private final VBox body = new VBox(14);
    private final Label telegramStatus = Ui.wrapped(I18n.t("common.loading"), "helper");
    private final VBox root;

    private final TextField daysField = new TextField();
    private final CheckBox emailBox = new CheckBox(I18n.t("reminders.email"));
    private final TextField timeField = new TextField();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final List<ToggleButton> weekdayButtons = new ArrayList<>();
    private final TreeSet<LocalDate> dates = new TreeSet<>();
    private final FlowPane dateChips = new FlowPane(6, 6);
    private final VBox weekdaysBox = new VBox(6);
    private final VBox datesBox = new VBox(8);
    private final InfoBar infoBar = new InfoBar();
    private final Button saveButton = new Button(I18n.t("common.save"));
    private ReminderSettings loaded;

    public ReminderSettingsCard(ProfileService profileService, ModalHost host) {
        this.profileService = profileService;
        this.host = host;

        HBox header = new HBox(12, Ui.iconTile(Icons.BELL, 36), Ui.label(I18n.t("reminders.title"), "heading-sm"));
        header.setAlignment(Pos.CENTER_LEFT);
        HBox telegram = new HBox(10, Icons.create(Icons.INFO, 13),
                new VBox(2, Ui.label(I18n.t("reminders.telegram"), "ledger-title"), telegramStatus));
        telegram.setAlignment(Pos.TOP_LEFT);

        root = new VBox(16, header, Ui.wrapped(I18n.t("reminders.text"), "helper"), body, Ui.hairline(), telegram);
        root.getStyleClass().add("glass-panel");
        root.setPadding(new Insets(22, 24, 22, 24));
        load();
    }

    public Node getRoot() {
        return root;
    }

    private void load() {
        body.getChildren().setAll(Ui.label(I18n.t("common.loading"), "helper"));
        BackgroundTask.run(profileService::getReminderSettings,
                settings -> {
                    loaded = settings;
                    buildForm(settings);
                },
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("reminders.unavailable", message));
                    body.getChildren().setAll(error);
                });
        BackgroundTask.run(profileService::getTelegramStatus,
                status -> telegramStatus.setText(telegramText(status)),
                message -> telegramStatus.setText(I18n.t("common.loadFailed", message)));
    }

    private static String telegramText(TelegramStatus status) {
        return switch (status) {
            case LINKED -> I18n.t("reminders.telegram.linked");
            case NOT_LINKED -> I18n.t("reminders.telegram.notLinked");
            case UNAVAILABLE -> I18n.t("reminders.telegram.unavailable");
        };
    }

    // ------------------------------------------------------------------ Form

    private void buildForm(ReminderSettings settings) {
        daysField.setText(String.valueOf(settings.daysBefore()));
        daysField.setPrefColumnCount(4);
        emailBox.setSelected(settings.emailEnabled());

        VBox daysRow = Ui.field(I18n.t("reminders.days"), daysField);
        daysField.setMaxWidth(120);
        body.getChildren().setAll(daysRow, emailBox);

        if (!settings.scheduleSupported()) {
            InfoBar notice = new InfoBar();
            notice.show(InfoBar.Severity.INFO, I18n.t("reminders.scheduleUnavailable"));
            body.getChildren().add(notice);
        } else {
            body.getChildren().addAll(scheduleFields(settings));
        }

        saveButton.getStyleClass().add("cta");
        saveButton.setOnAction(event -> save());
        HBox actions = new HBox(saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        body.getChildren().addAll(infoBar, actions);
    }

    private List<Node> scheduleFields(ReminderSettings settings) {
        timeField.setText(settings.time().toString());
        timeField.setPromptText("08:00");
        timeField.setMaxWidth(120);

        HBox modes = Ui.segmented();
        for (ReminderMode mode : ReminderMode.values()) {
            ToggleButton segment = new ToggleButton(mode.toString());
            segment.setUserData(mode);
            segment.setToggleGroup(modeGroup);
            segment.setSelected(mode == settings.mode());
            segment.getStyleClass().add("segment");
            segment.setOnAction(event -> {
                segment.setSelected(true);
                updateModeVisibility();
            });
            modes.getChildren().add(segment);
        }

        FlowPane days = new FlowPane(4, 4);
        weekdayButtons.clear();
        for (DayOfWeek day : DayOfWeek.values()) {
            ToggleButton chip = new ToggleButton(day.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale()));
            chip.getStyleClass().add("filter-chip");
            chip.setUserData(day.getValue());
            chip.setSelected(settings.weekdays().contains(day.getValue()));
            weekdayButtons.add(chip);
            days.getChildren().add(chip);
        }
        weekdaysBox.getChildren().setAll(Ui.label(I18n.t("reminders.weekdays"), "field-label"), days);

        dates.clear();
        dates.addAll(settings.dates());
        DatePicker picker = new DatePicker();
        picker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        Button add = new Button(I18n.t("reminders.addDate"), Icons.create(Icons.ADD, 12));
        add.setOnAction(event -> addDate(picker));
        HBox addRow = new HBox(8, picker, add);
        HBox.setHgrow(picker, Priority.ALWAYS);
        datesBox.getChildren().setAll(Ui.label(I18n.t("reminders.dates"), "field-label"), addRow, dateChips);
        refreshDateChips();
        updateModeVisibility();

        return List.of(Ui.field(I18n.t("reminders.time"), timeField),
                Ui.wrapped(I18n.t("reminders.time.hint"), "helper"),
                Ui.field(I18n.t("reminders.when"), modes),
                weekdaysBox, datesBox);
    }

    private void addDate(DatePicker picker) {
        LocalDate date;
        try {
            date = DebtForm.readDate(picker);
        } catch (ValidationException e) {
            infoBar.show(InfoBar.Severity.ERROR, e.getMessage());
            return;
        }
        if (date == null) {
            return;
        }
        if (date.isBefore(LocalDate.now())) {
            infoBar.show(InfoBar.Severity.ERROR, I18n.t("reminders.error.pastDate"));
            return;
        }
        infoBar.hide();
        dates.add(date);
        picker.setValue(null);
        picker.getEditor().clear();
        refreshDateChips();
    }

    private void refreshDateChips() {
        dateChips.getChildren().clear();
        for (LocalDate date : dates) {
            Button chip = new Button(Formats.date(date), Icons.create(Icons.CLOSE, 9));
            chip.getStyleClass().add("filter-chip");
            Ui.tooltip(chip, I18n.t("reminders.removeDate"));
            chip.setOnAction(event -> {
                dates.remove(date);
                refreshDateChips();
            });
            dateChips.getChildren().add(chip);
        }
        if (dates.isEmpty()) {
            dateChips.getChildren().add(Ui.label(I18n.t("reminders.noDates"), "helper"));
        }
    }

    private ReminderMode selectedMode() {
        return modeGroup.getSelectedToggle() == null ? ReminderMode.DAILY
                : (ReminderMode) modeGroup.getSelectedToggle().getUserData();
    }

    private void updateModeVisibility() {
        ReminderMode mode = selectedMode();
        weekdaysBox.setVisible(mode == ReminderMode.WEEKDAYS);
        weekdaysBox.setManaged(mode == ReminderMode.WEEKDAYS);
        datesBox.setVisible(mode == ReminderMode.DATES);
        datesBox.setManaged(mode == ReminderMode.DATES);
    }

    // ------------------------------------------------------------------ Saving

    private void save() {
        ReminderSettings settings;
        try {
            settings = readForm();
        } catch (ValidationException e) {
            infoBar.show(InfoBar.Severity.ERROR, e.getMessage());
            return;
        }
        infoBar.hide();
        saveButton.setDisable(true);
        BackgroundTask.run(() -> {
                    profileService.saveReminderSettings(settings);
                    return null;
                },
                result -> {
                    saveButton.setDisable(false);
                    host.toast(I18n.t("reminders.saved"), false);
                },
                message -> {
                    saveButton.setDisable(false);
                    infoBar.show(InfoBar.Severity.ERROR, message);
                });
    }

    private ReminderSettings readForm() {
        int days = profileService.parseDaysBefore(daysField.getText());
        if (!loaded.scheduleSupported()) {
            return new ReminderSettings(days, emailBox.isSelected(), false, loaded.time(), loaded.timeZone(),
                    loaded.mode(), loaded.weekdays(), loaded.dates());
        }
        LocalTime time = profileService.parseTime(timeField.getText());
        List<Integer> weekdays = new ArrayList<>();
        for (ToggleButton button : weekdayButtons) {
            if (button.isSelected()) {
                weekdays.add((Integer) button.getUserData());
            }
        }
        return new ReminderSettings(days, emailBox.isSelected(), true, time, loaded.timeZone(),
                selectedMode(), weekdays, List.copyOf(dates));
    }
}
