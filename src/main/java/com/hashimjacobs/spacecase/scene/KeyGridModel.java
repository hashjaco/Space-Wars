package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.engine.GamepadMapping;

/**
 * The rules behind the on-screen keyboard: a grid of characters, a cursor, and what has been typed.
 *
 * Apart from {@link OnScreenKeyboard} for the reason {@code LobbyModel} is apart from
 * {@code LobbyPanel} -- so the arithmetic can be tested without starting the JavaFX toolkit, which
 * {@code docs/ROADMAP.md} rule 6 forbids the suite from doing. {@link KeyCode} is fine here; a
 * {@code Font} or a {@code Scene} would not be.
 *
 * <h2>Why this screen exists at all</h2>
 *
 * Name and code entry used to read letters straight off the keystroke. Player one's pad speaks
 * {@code W A S D} -- those are the ship's keys, and {@link GamepadMapping} has nothing else to say --
 * so its d-pad typed four letters into the field and never moved the cursor once. A panel cannot tell
 * the two apart: the pad fires real key events at the Scene and {@code SceneRouter} passes on only
 * the {@link KeyCode}. Routing every character through a grid retires the ambiguity rather than
 * arbitrating it, and W means "up" everywhere in the game again.
 */
final class KeyGridModel {

    /**
     * Cells per row.
     *
     * Eight divides the 32-character code alphabet exactly and leaves the 37-character name alphabet
     * a short last row, which the cursor handles -- see {@link #clampColumn}. Wider would need a card
     * past {@code Tokens.ROW_WIDTH}; narrower would add rows, and rows cost more to cross than
     * columns do, because a held direction only repeats about three times a second.
     */
    static final int COLUMNS = 8;

    static final String DELETE = "DEL";
    static final String CLEAR = "CLEAR";
    static final String DONE = "DONE";

    private final int maxLength;
    private final List<List<String>> rows = new ArrayList<>();
    private final StringBuilder typed = new StringBuilder();

    private int row;
    private int column;
    private boolean done;

    /**
     * @param alphabet the characters this field accepts, in the order they are laid out
     * @param maxLength the longest value the field will hold
     * @param initial what the field already contains, kept so opening the keyboard on a name is an
     *                edit rather than a fresh start
     */
    KeyGridModel(String alphabet, int maxLength, String initial) {
        this.maxLength = maxLength;
        for (int at = 0; at < alphabet.length(); at += COLUMNS) {
            List<String> line = new ArrayList<>();
            for (int column = at; column < Math.min(at + COLUMNS, alphabet.length()); column++) {
                line.add(String.valueOf(alphabet.charAt(column)));
            }
            rows.add(List.copyOf(line));
        }
        rows.add(List.of(DELETE, CLEAR, DONE));

        typed.append(initial, 0, Math.min(initial.length(), maxLength));
        // On DONE rather than on A: somebody who has just opened the card to change one letter is
        // one press from leaving, and a pad walking up to the letters passes the alphabet on the way.
        row = rows.size() - 1;
        column = rows.get(row).indexOf(DONE);
    }

    String typed() {
        return typed.toString();
    }

    int row() {
        return row;
    }

    int column() {
        return column;
    }

    /** The grid in display order: the alphabet in rows of {@link #COLUMNS}, then the action row. */
    List<List<String>> rows() {
        return List.copyOf(rows);
    }

    /** Whether the player has finished, by pressing DONE, Escape or the pad's cancel button. */
    boolean isDone() {
        return done;
    }

    /**
     * Puts the cursor on a cell directly, for the mouse.
     *
     * A click has to move the cursor as well as press the cell, or the next repaint would throw the
     * highlight back to wherever the arrows last left it. Out-of-range is ignored rather than thrown,
     * the same bargain {@link MenuNavigator#focus} makes.
     */
    void moveTo(int row, int column) {
        if (row >= 0 && row < rows.size() && column >= 0 && column < rows.get(row).size()) {
            this.row = row;
            this.column = column;
        }
    }

    /**
     * @return true when the key was used here and must not reach the menu underneath
     */
    boolean handleKey(KeyCode code) {
        if (code == GamepadMapping.MENU_CANCEL) {
            done = true;
            return true;
        }
        switch (code) {
            // Both spellings of every direction, because player one's pad speaks WASD and player
            // two's speaks the arrows. Consumed even when the move changes nothing, so a direction
            // can never fall through and scroll the menu hidden behind this card.
            case UP, W -> step(-1, 0);
            case DOWN, S -> step(1, 0);
            case LEFT, A -> step(0, -1);
            case RIGHT, D -> step(0, 1);
            case ENTER, SPACE -> press(rows.get(row).get(column));
            case ESCAPE -> done = true;
            case BACK_SPACE -> press(DELETE);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Presses a cell by its label, which is how the mouse and the keyboard both arrive. */
    void press(String cell) {
        switch (cell) {
            case DELETE -> {
                if (typed.length() > 0) {
                    typed.deleteCharAt(typed.length() - 1);
                }
            }
            case CLEAR -> typed.setLength(0);
            case DONE -> done = true;
            default -> {
                if (typed.length() < maxLength) {
                    typed.append(cell);
                }
            }
        }
    }

    /**
     * Moves the cursor one cell, wrapping at every edge.
     *
     * Wrapping rather than stopping because a grid is crossed on a d-pad at about three cells a
     * second: without it, reaching Z from A is seven presses instead of one.
     */
    private void step(int rowDelta, int columnDelta) {
        if (rowDelta != 0) {
            int count = rows.size();
            row = ((row + rowDelta) % count + count) % count;
            column = clampColumn(column);
            return;
        }
        int count = rows.get(row).size();
        column = ((column + columnDelta) % count + count) % count;
    }

    /**
     * Holds the column inside a row that is shorter than the one above it.
     *
     * The action row has three cells and the name alphabet's last row has five, so a cursor coming
     * down from column seven has nowhere to land. It slides to the end of the row rather than
     * wrapping around to the start, which is where the eye expects it and keeps the move reversible.
     */
    private int clampColumn(int wanted) {
        return Math.min(wanted, rows.get(row).size() - 1);
    }
}
