package com.hashimjacobs.spacecase.scene;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.engine.GamepadMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grid a controller walks to enter a name.
 *
 * The bug this whole screen exists to fix was that player one's d-pad, which speaks {@code W A S D},
 * typed four letters into the field instead of moving anything. So the first thing asserted here is
 * that {@code W} moves the cursor and appends nothing -- and {@code MenuNavigatorTest} asserts the
 * same for the menus behind it. Between them, W means "up" everywhere in the game.
 */
class KeyGridModelTest {

    /** A-Z, the digits and a space: 37 cells, which leaves the last alphabet row five of eight. */
    private static final String NAMES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";

    /** The relay's room-code alphabet: exactly 32, so it tiles four full rows. */
    private static final String CODES = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static KeyGridModel names() {
        return new KeyGridModel(NAMES, 10, "");
    }

    /** Puts the cursor on a known cell, since a fresh model starts on DONE. */
    private static KeyGridModel at(KeyGridModel grid, int row, int column) {
        grid.moveTo(row, column);
        return grid;
    }

    @Test
    void theDirectionKeysBothPadsSendMoveTheCursorAndTypeNothing() {
        for (KeyCode code : new KeyCode[] {KeyCode.W, KeyCode.A, KeyCode.S, KeyCode.D,
                                           KeyCode.UP, KeyCode.LEFT, KeyCode.DOWN, KeyCode.RIGHT}) {
            KeyGridModel grid = at(names(), 1, 3);
            assertTrue(grid.handleKey(code), code + " must never reach the menu behind the card");
            assertEquals("", grid.typed(), code + " is a direction here, not a letter");
        }
    }

    @Test
    void wAndSWalkTheRowsAndAAndDWalkTheColumns() {
        KeyGridModel grid = at(names(), 1, 3);

        grid.handleKey(KeyCode.W);
        assertEquals(0, grid.row());
        assertEquals(3, grid.column());

        grid.handleKey(KeyCode.D);
        assertEquals(0, grid.row());
        assertEquals(4, grid.column());

        grid.handleKey(KeyCode.S);
        assertEquals(1, grid.row());

        grid.handleKey(KeyCode.A);
        assertEquals(3, grid.column());
    }

    @Test
    void theCursorWrapsAtEveryEdgeRatherThanStopping() {
        KeyGridModel grid = at(names(), 0, 0);

        grid.handleKey(KeyCode.LEFT);
        assertEquals(KeyGridModel.COLUMNS - 1, grid.column(), "left off the start lands on the end");

        grid.handleKey(KeyCode.RIGHT);
        assertEquals(0, grid.column());

        grid.handleKey(KeyCode.UP);
        assertEquals(grid.rows().size() - 1, grid.row(), "up off the top lands on the action row");

        grid.handleKey(KeyCode.DOWN);
        assertEquals(0, grid.row());
    }

    /**
     * The one piece of arithmetic with somewhere to go wrong.
     *
     * 37 characters into rows of eight leaves five in the last one, and the action row holds three.
     * A cursor in column seven coming down has no cell under it, and must slide to the end of the
     * row rather than wrap round to the start or walk off the array.
     */
    @Test
    void aColumnWithNoCellUnderItSlidesToTheEndOfTheShorterRow() {
        KeyGridModel grid = names();
        int lastAlphabetRow = grid.rows().size() - 2;
        int actionRow = grid.rows().size() - 1;
        assertEquals(5, grid.rows().get(lastAlphabetRow).size(), "37 characters, eight per row");
        assertEquals(3, grid.rows().get(actionRow).size(), "DEL, CLEAR and DONE");

        at(grid, lastAlphabetRow - 1, 7).handleKey(KeyCode.DOWN);
        assertEquals(lastAlphabetRow, grid.row());
        assertEquals(4, grid.column(), "column seven has nowhere to land in a five-cell row");

        grid.handleKey(KeyCode.DOWN);
        assertEquals(actionRow, grid.row());
        assertEquals(2, grid.column(), "and nowhere but DONE in a three-cell one");
    }

    @Test
    void theCodeAlphabetTilesWithNoShortRowAtAll() {
        KeyGridModel grid = new KeyGridModel(CODES, 6, "");
        for (int row = 0; row < grid.rows().size() - 1; row++) {
            assertEquals(KeyGridModel.COLUMNS, grid.rows().get(row).size(),
                    "32 characters is exactly four full rows");
        }
    }

    @Test
    void pressingACellAppendsItAndStopsAtTheMaximum() {
        KeyGridModel grid = new KeyGridModel(NAMES, 3, "");
        at(grid, 0, 0).handleKey(KeyCode.ENTER);
        at(grid, 0, 1).handleKey(KeyCode.SPACE);
        at(grid, 0, 2).handleKey(KeyCode.ENTER);
        assertEquals("ABC", grid.typed());

        at(grid, 0, 3).handleKey(KeyCode.ENTER);
        assertEquals("ABC", grid.typed(), "a fourth character has nowhere to go");
    }

    @Test
    void deleteRemovesTheLastCharacterAndDoesNothingOnAnEmptyField() {
        KeyGridModel grid = new KeyGridModel(NAMES, 10, "AB");

        grid.press(KeyGridModel.DELETE);
        assertEquals("A", grid.typed());

        grid.handleKey(KeyCode.BACK_SPACE);
        grid.handleKey(KeyCode.BACK_SPACE);
        assertEquals("", grid.typed(), "deleting an empty field must not throw");
    }

    @Test
    void clearEmptiesTheFieldInOnePress() {
        KeyGridModel grid = new KeyGridModel(NAMES, 10, "MAVERICK");
        grid.press(KeyGridModel.CLEAR);
        assertEquals("", grid.typed());
    }

    @Test
    void anExistingNameIsOpenedForEditingRatherThanReplaced() {
        assertEquals("MAVERICK", new KeyGridModel(NAMES, 10, "MAVERICK").typed());
        assertEquals("MAVERICKX", new KeyGridModel(NAMES, 9, "MAVERICKXY").typed(),
                "a name longer than the field is clipped, not carried");
    }

    @Test
    void theCursorOpensOnDoneSoOneKeypressLeaves() {
        KeyGridModel grid = names();
        assertEquals(KeyGridModel.DONE, grid.rows().get(grid.row()).get(grid.column()));
        assertFalse(grid.isDone());

        grid.handleKey(KeyCode.ENTER);
        assertTrue(grid.isDone());
    }

    /** Escape is the keyboard's way out and CANCEL is the pad's B button. Both mean finished. */
    @Test
    void escapeAndThePadsCancelBothFinish() {
        assertTrue(finishedWith(KeyCode.ESCAPE));
        assertTrue(finishedWith(GamepadMapping.MENU_CANCEL));
    }

    private static boolean finishedWith(KeyCode code) {
        KeyGridModel grid = at(names(), 0, 0);
        assertTrue(grid.handleKey(code), code + " must not reach the menu behind the card");
        return grid.isDone();
    }

    @Test
    void aKeyTheGridHasNoUseForFallsThroughToTheScreen() {
        assertFalse(names().handleKey(KeyCode.F1));
        assertFalse(names().handleKey(KeyCode.TAB));
    }
}
