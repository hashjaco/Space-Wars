package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Rank;

/**
 * Names the two seats, and shows what each pilot has earned so far.
 *
 * Choosing a seat opens {@link OnScreenKeyboard}. Letters used to type straight into the focused row
 * instead, which read as the shorter way round until a controller was plugged in: player one's d-pad
 * is {@code W A S D}, so it wrote four letters into the name and never moved the cursor. Nothing on
 * this screen reads a letter any more -- see {@link KeyGridModel}.
 */
final class NameEntryPanel extends VBox {

    private static final int SEATS = 2;

    /**
     * What a pilot name may contain: A-Z, the digits, and a space.
     *
     * No punctuation, because the leaderboard's server strips anything outside {@code [A-Z0-9 ]}
     * while {@link Pilots#careerScore} keys on what was typed -- a hyphen would score under one name
     * locally and appear on the board under another.
     */
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";

    private final Pilots pilots;
    private final List<MenuButton> rows = new ArrayList<>();
    private final List<StringBuilder> typed = new ArrayList<>();
    private final Text standing;
    private final MenuNavigator navigator;

    /** The screen root the keyboard mounts into. Null until the router says which one. */
    private StackPane overlayHost;
    private OnScreenKeyboard keyboard;

    NameEntryPanel(Pilots pilots, Runnable onBack) {
        super(12);
        this.pilots = pilots;
        setAlignment(Pos.CENTER);

        List<MenuButton> items = new ArrayList<>();
        for (int seat = 1; seat <= SEATS; seat++) {
            // An unnamed seat starts empty so the keyboard opens on a blank field rather than on the
            // default; the default is still what the row shows and what gets used.
            String existing = pilots.isNamed(seat) ? pilots.name(seat) : "";
            typed.add(new StringBuilder(existing));
            int index = seat - 1;
            MenuButton row = new MenuButton("", () -> edit(index));
            rows.add(row);
            items.add(row);
        }
        Runnable leave = () -> {
            commit();
            onBack.run();
        };
        items.add(new MenuButton("Back", leave));

        MenuPanel panel = new MenuPanel(items.toArray(new MenuButton[0]));
        standing = MenuScreen.caption("", 12, Tokens.TEXT_DIM);
        getChildren().addAll(panel, standing);

        navigator = panel.navigator();
        navigator.setOnBack(leave);
        refresh();
    }

    /** Where the keyboard mounts: the screen root, which {@code SceneRouter} owns. */
    void setOverlayHost(StackPane overlayHost) {
        this.overlayHost = overlayHost;
    }

    /**
     * This screen's keys.
     *
     * The keyboard gets them all while it is up, so a direction cannot fall through and scroll the
     * menu behind the card.
     */
    boolean handleKey(KeyCode code) {
        if (keyboard != null) {
            return keyboard.handleKey(code);
        }
        boolean handled = navigator.handleKey(code);
        if (handled) {
            refresh();
        }
        return handled;
    }

    /** Opens the keyboard on one seat's name. */
    private void edit(int seat) {
        if (overlayHost == null || keyboard != null) {
            return;
        }
        keyboard = new OnScreenKeyboard(overlayHost, "PLAYER " + (seat + 1),
                ALPHABET, Pilots.MAX_NAME_LENGTH, typed.get(seat).toString(), entered -> {
                    // Trimmed here rather than only in Pilots.setName, so the rank shown under the
                    // row is looked up on the same string that will be stored.
                    typed.get(seat).setLength(0);
                    typed.get(seat).append(entered.trim());
                    keyboard = null;
                    refresh();
                });
    }

    private void refresh() {
        for (int seat = 0; seat < SEATS; seat++) {
            String name = typed.get(seat).toString();
            String shown = name.isEmpty() ? pilots.name(seat + 1) : name;
            rows.get(seat).setText("PLAYER " + (seat + 1) + "        " + shown);
        }

        int focused = navigator.focusedIndex();
        if (focused >= SEATS) {
            standing.setText("Choose a seat to rename it");
            return;
        }
        String name = typed.get(focused).toString();
        if (name.isEmpty()) {
            standing.setText("Enter to rename    replaces the default");
            return;
        }
        int career = pilots.careerScore(name);
        Rank rank = Rank.forCareerScore(career);
        standing.setText(rank.label() + "    career " + career + "    Enter to rename");
    }

    /** Writes both names back on the way out, so a half-typed name is never saved mid-edit. */
    private void commit() {
        for (int seat = 0; seat < SEATS; seat++) {
            pilots.setName(seat + 1, typed.get(seat).toString());
        }
    }
}
