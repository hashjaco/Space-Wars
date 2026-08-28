package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;

import com.hashimjacobs.spacecase.ui.MenuWindow;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A vertical stack of menu entries on a single card.
 *
 * Ported from the original MenuBox. A panel longer than {@link #VISIBLE_ROWS} shows a window onto
 * its entries rather than running off the bottom of the screen -- see {@link MenuWindow}, and note
 * that the entries the navigator walks are always the whole list. Only what is mounted changes.
 *
 * Rows are unmounted rather than clipped and slid: a button that is off screen but still in the
 * scene keeps taking mouse events, and the pixel arithmetic would have to know the row pitch, which
 * is the sort of number that goes stale the first time a button changes height.
 *
 * There is no rule between rows any more. There used to be one above and below every entry, drawn
 * at 330px against a 320px button -- two magic numbers in two files with an implied relationship,
 * visibly misaligned by five pixels at each end. The rows have their own edges now, so the gap
 * between them does the separating and there is nothing left to keep in step.
 */
public final class MenuPanel extends VBox {

    /**
     * How many rows fit under a title on an 864px stage, with room to spare.
     *
     * Settings is the panel that reaches this; every other menu in the game is comfortably short.
     *
     * The arithmetic, so the next change to a row pitch has something to check against:
     * 12 rows x ROW_MENU 42, plus 11 gaps x GAP_XS 4, plus GAP 12 of card padding top and bottom,
     * is 572. A title plate runs about 67 and the screen column adds GAP_L 18, so roughly 657 of
     * the 864 available. The README records a LAUNCH button that scrolled off the bottom, so this
     * number is checked by rendering the settings screen, not by trusting the sum.
     */
    private static final int VISIBLE_ROWS = 12;

    private final List<MenuButton> buttons;
    private final List<MenuNavigator.Item> items = new ArrayList<>();
    private final MenuWindow window;

    public MenuPanel(MenuButton... buttons) {
        setAlignment(Pos.CENTER);
        setSpacing(Tokens.GAP_XS);
        setPadding(new Insets(Tokens.GAP));
        // A card, so the menu reads as one panel over a lit backdrop rather than rows floating on
        // the sky. Same rounded-panel idiom the system map already draws on its canvas.
        CornerRadii corners = new CornerRadii(Tokens.RADIUS_L);
        setBackground(new Background(new BackgroundFill(
                Tokens.SURFACE_1.deriveColor(0, 1, 1, 0.55), corners, Insets.EMPTY)));
        setBorder(new Border(new BorderStroke(Tokens.EDGE, BorderStrokeStyle.SOLID, corners,
                new BorderWidths(Tokens.STROKE_HAIR))));
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
        int first = window.firstVisible();
        for (int row = first; row < first + window.visibleRows(); row++) {
            getChildren().add(buttons.get(row));
        }
    }
}
