package kz.kvit.ui;

import javafx.scene.Node;

/**
 * A section of the main window. The keyboard shortcuts of the main window
 * (Ctrl+N, Ctrl+F, F5) call the matching method of the visible page.
 */
public interface Page {
    Node getRoot();

    /** F5 */
    default void refresh() {
    }

    /** Ctrl+N */
    default void createNew() {
    }

    /** Ctrl+F */
    default void focusSearch() {
    }
}
