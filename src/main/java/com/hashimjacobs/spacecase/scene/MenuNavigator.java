package com.hashimjacobs.spacecase.scene;

import java.util.List;

import javafx.scene.input.KeyCode;

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

    public MenuNavigator(List<Item> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a menu needs at least one item");
        }
        this.items = List.copyOf(items);
        applyFocus();
    }

    /** Runs when Escape is pressed. Defaults to doing nothing. */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    /** Returns true when the key was a navigation key and should not travel any further. */
    public boolean handleKey(KeyCode code) {
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
    }

    public int focusedIndex() {
        return index;
    }
}
