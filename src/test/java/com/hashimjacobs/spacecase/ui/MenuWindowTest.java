package com.hashimjacobs.spacecase.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window a long menu shows through. Toolkit-free, like MenuNavigatorTest.
 */
class MenuWindowTest {

    @Test
    void aMenuThatFitsNeverScrolls() {
        MenuWindow window = new MenuWindow(5, 12);

        assertFalse(window.scrolls());
        assertEquals(5, window.visibleRows());
        window.follow(4);
        assertEquals(0, window.firstVisible());
    }

    @Test
    void movingInsideTheWindowLeavesItAlone() {
        MenuWindow window = new MenuWindow(20, 12);

        window.follow(11);

        assertEquals(0, window.firstVisible(), "the last visible row is still visible");
    }

    @Test
    void steppingPastTheBottomScrollsByExactlyOneRow() {
        MenuWindow window = new MenuWindow(20, 12);

        window.follow(12);

        assertEquals(1, window.firstVisible());
    }

    @Test
    void steppingBackAboveTheTopScrollsBackByOne() {
        MenuWindow window = new MenuWindow(20, 12);
        window.follow(15);
        assertEquals(4, window.firstVisible());

        window.follow(3);

        assertEquals(3, window.firstVisible());
    }

    /** The navigator sends the cursor from the last row to the first in one step. */
    @Test
    void aWrapFromTheEndBackToTheStartLandsInsideTheWindow() {
        MenuWindow window = new MenuWindow(20, 12);
        window.follow(19);
        assertEquals(8, window.firstVisible());

        window.follow(0);

        assertEquals(0, window.firstVisible());
    }

    @Test
    void theFocusedRowIsAlwaysInsideTheWindow() {
        MenuWindow window = new MenuWindow(30, 12);
        int[] visits = {0, 29, 5, 28, 14, 0, 13, 12, 11};

        for (int focused : visits) {
            window.follow(focused);
            int first = window.firstVisible();
            assertTrue(focused >= first && focused < first + window.visibleRows(),
                    "row " + focused + " fell outside a window starting at " + first);
        }
    }

    @Test
    void aWindowHasToShowSomething() {
        assertThrows(IllegalArgumentException.class, () -> new MenuWindow(10, 0));
    }
}
