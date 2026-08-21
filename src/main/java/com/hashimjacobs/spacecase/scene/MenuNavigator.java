package com.hashimjacobs.spacecase.scene;

import java.util.List;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.engine.GamepadMapping;

/**
 * Keyboard focus for a menu: move with the arrows or W/S, choose with Enter or Space, leave with
 * Escape.
 *
 * The menus were mouse-only, which is odd for a game played entirely on the keyboard. Items are
 * reached through {@link Item} rather than as concrete buttons so the navigation rules can be tested
 * without starting the JavaFX toolkit.
 */
public final class MenuNavigator {

    /** Something focusable in a menu. {@link MenuButton} is the only real implementation. */
    public interface Item {
        void setHighlighted(boolean highlighted);

        void activate();
    }

    private final List<Item> items;
    private int index;
    private Runnable onBack = () -> {
    };
    private java.util.function.IntConsumer onFocusMoved = focused -> {
    };

    public MenuNavigator(List<Item> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a menu needs at least one item");
        }
        this.items = List.copyOf(items);
        applyFocus();
    }

    /**
     * Runs with the newly focused row whenever focus lands, including on construction.
     *
     * How a panel too long to fit follows the cursor. A callback rather than the panel polling,
     * because focus moves are the only thing that can scroll it.
     */
    public void setOnFocusMoved(java.util.function.IntConsumer onFocusMoved) {
        this.onFocusMoved = onFocusMoved;
        onFocusMoved.accept(index);
    }

    /** Runs when Escape is pressed. Defaults to doing nothing. */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    /** Returns true when the key was a navigation key and should not travel any further. */
    public boolean handleKey(KeyCode code) {
        // Ahead of the switch rather than a case label beside ESCAPE, so the code stays named where
        // it is defined instead of being spelled out again here.
        if (code == GamepadMapping.MENU_CANCEL) {
            onBack.run();
            return true;
        }
        switch (code) {
            case UP, W -> move(-1);
            case DOWN, S -> move(1);
            case ENTER, SPACE -> items.get(index).activate();
            case ESCAPE -> onBack.run();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void move(int delta) {
        int count = items.size();
        // Wrapping keeps a short menu navigable without hunting for the end.
        index = ((index + delta) % count + count) % count;
        applyFocus();
    }

    private void applyFocus() {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setHighlighted(i == index);
        }
        onFocusMoved.accept(index);
    }

    public int focusedIndex() {
        return index;
    }
}
