package com.hashimjacobs.spacecase.ui;

/**
 * Which slice of a long menu is on screen.
 *
 * Kept apart from the panel that draws it so the arithmetic can be tested without starting the
 * JavaFX toolkit, the same bargain the navigator's Item interface makes for the navigation rules.
 *
 * Lives in ui rather than scene because the garage scrolls too, and it is drawn on a canvas rather
 * than built from nodes -- a shared package beats garage reaching into scene.
 *
 * Scrolls the least it can: a cursor moving inside the window does not move the window, which is
 * what stops a menu sliding under the reader on every keypress. The one case worth spelling out is
 * the wrap -- the navigator sends the cursor from the last row to the first in one step, so
 * this has to handle a jump of any size, not just a step of one.
 */
public final class MenuWindow {

    private final int rows;
    private final int capacity;
    private int first;

    public MenuWindow(int rows, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("a window has to show at least one row");
        }
        this.rows = rows;
        this.capacity = capacity;
    }

    /** Moves the window as little as it can to bring the focused row inside it. */
    public void follow(int focused) {
        if (focused < first) {
            first = focused;
        } else if (focused >= first + capacity) {
            first = focused - capacity + 1;
        }
    }

    public int firstVisible() {
        return first;
    }

    public int visibleRows() {
        return Math.min(capacity, rows);
    }

    /** False when everything fits, which is every menu in the game but the long ones. */
    public boolean scrolls() {
        return rows > capacity;
    }
}
