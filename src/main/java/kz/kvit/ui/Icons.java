package kz.kvit.ui;

import javafx.scene.control.Label;

/** Glyphs of the "Segoe Fluent Icons" font that ships with Windows 11. */
public final class Icons {
    public static final String HOME = "\uE80F";
    public static final String BANK = "\uE825";
    public static final String LIST = "\uE8FD";
    public static final String PAYMENT = "\uE8C7";
    public static final String REPORT = "\uE9F9";
    public static final String CHART = "\uE9D2";
    public static final String CALENDAR = "\uE787";
    public static final String REFRESH = "\uE72C";
    public static final String LOCK = "\uE72E";
    public static final String CONTACT = "\uE77B";
    public static final String CHECK = "\uE73E";
    public static final String ERROR = "\uE783";
    public static final String INFO = "\uE946";
    public static final String WARNING = "\uE7BA";
    public static final String SIGN_OUT = "\uF3B1";
    public static final String ADD = "\uE710";
    public static final String EDIT = "\uE70F";
    public static final String DELETE = "\uE74D";
    public static final String SEARCH = "\uE721";
    public static final String SUN = "\uE706";
    public static final String MOON = "\uE708";
    public static final String GLOBE = "\uE774";
    public static final String SETTINGS = "\uE713";
    public static final String EXPORT = "\uEDE1";
    public static final String CLOSE = "\uE711";
    public static final String HISTORY = "\uE81C";
    public static final String TIMER = "\uE916";
    public static final String SYSTEM = "\uE7F4";
    public static final String CHEVRON_LEFT = "\uE76B";
    public static final String CHEVRON_RIGHT = "\uE76C";
    public static final String EYE = "\uE890";
    public static final String EYE_OFF = "\uED1A";
    public static final String CALCULATOR = "\uE8EF";

    private Icons() {
    }

    public static Label create(String glyph, double size) {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("icon");
        icon.setStyle("-fx-font-size: " + size + "px;");
        icon.setMinWidth(Label.USE_PREF_SIZE);
        return icon;
    }
}
