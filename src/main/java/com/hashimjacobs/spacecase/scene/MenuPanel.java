package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

import com.hashimjacobs.spacecase.ui.MenuWindow;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A vertical stack of menu entries separated by hairlines.
 *
 * Ported from the original MenuBox. A panel longer than {@link #VISIBLE_ROWS} shows a window onto
 * its entries rather than running off the bottom of the screen -- see {@link MenuWindow}, and note
 * that the entries the navigator walks are always the whole list. Only what is mounted changes.
 *
 * Rows are unmounted rather than clipped and slid: a button that is off screen but still in the
 * scene keeps taking mouse events, and the pixel arithmetic would have to know the row pitch, which
 * is the sort of number that goes stale the first time a button changes height.
 */
public final class MenuPanel extends VBox {

    /**
     * How many rows fit under a title on an 864px stage, with room to spare.
     *
     * Settings is the panel that reaches this; every other menu in the game is comfortably short.
     */
    private static final int VISIBLE_ROWS = 12;

    private final List<MenuButton> buttons;
    private final List<MenuNavigator.Item> items = new ArrayList<>();
    private final MenuWindow window;

    public MenuPanel(MenuButton... buttons) {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 0, 8, 0));
        this.buttons = List.of(buttons);
        this.items.addAll(this.buttons);
        this.window = new MenuWindow(this.buttons.size(), VISIBLE_ROWS);
        mountWindow();
    }

    /** The entries in display order, for {@link MenuNavigator} to walk. All of them, mounted or not. */
    public List<MenuNavigator.Item> items() {
        return List.copyOf(items);
    }

    /** A navigator over this panel's entries, with the first one focused. */
    public MenuNavigator navigator() {
        MenuNavigator navigator = new MenuNavigator(items());
        if (window.scrolls()) {
            navigator.setOnFocusMoved(this::scrollTo);
        }
        return navigator;
    }

    private void scrollTo(int focused) {
        int before = window.firstVisible();
        window.follow(focused);
        if (window.firstVisible() != before) {
            mountWindow();
        }
    }

    private void mountWindow() {
        getChildren().clear();
        getChildren().add(separator());
        int first = window.firstVisible();
        for (int row = first; row < first + window.visibleRows(); row++) {
            getChildren().addAll(buttons.get(row), separator());
        }
    }

    private static Line separator() {
        Line line = new Line();
        line.setEndX(330);
        line.setStroke(Tokens.EDGE_SOFT);
        return line;
    }
}
