package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Rank;

/**
 * Names the two seats, and shows what each pilot has earned so far.
 *
 * Letters type straight into the focused row rather than through an edit mode: there is nothing else
 * to do on this screen, and a mode would need its own way out.
 */
final class NameEntryPanel extends VBox {

    private static final int SEATS = 2;

    private final Pilots pilots;
    private final List<MenuButton> rows = new ArrayList<>();
    private final List<StringBuilder> typed = new ArrayList<>();
    private final Text standing;
    private final MenuNavigator navigator;

    NameEntryPanel(Pilots pilots, Runnable onBack) {
        super(12);
        this.pilots = pilots;
        setAlignment(Pos.CENTER);

        List<MenuButton> items = new ArrayList<>();
        for (int seat = 1; seat <= SEATS; seat++) {
            // An unnamed seat starts empty so the first keystroke replaces the default rather than
            // appending to it; the default is still what the row shows and what gets used.
            String existing = pilots.isNamed(seat) ? pilots.name(seat) : "";
            typed.add(new StringBuilder(existing));
            // No action: the row is a text field, and Enter only moves focus along.
            MenuButton row = new MenuButton("", () -> {
            });
            rows.add(row);
            items.add(row);
        }
        Runnable leave = () -> {
            commit();
            onBack.run();
        };
        items.add(new MenuButton("Back", leave));

        MenuPanel panel = new MenuPanel(items.toArray(new MenuButton[0]));
        standing = MenuScreen.caption("", 12, Color.web("#8b98ad"));
        getChildren().addAll(panel, standing);

        navigator = panel.navigator();
        navigator.setOnBack(leave);
        refresh();
    }

    /**
     * This screen's keyboard.
     *
     * Typing is offered the key before navigation, because {@link MenuNavigator} claims W and S to
     * move between rows and both are letters a name may well contain.
     */
    boolean handleKey(KeyCode code) {
        boolean handled = type(code) || navigator.handleKey(code);
        if (handled) {
            refresh();
        }
        return handled;
    }

    private boolean type(KeyCode code) {
        int focused = navigator.focusedIndex();
        if (focused >= SEATS) {
            return false;
        }
        StringBuilder name = typed.get(focused);

        if (code == KeyCode.BACK_SPACE) {
            if (name.length() > 0) {
                name.deleteCharAt(name.length() - 1);
            }
            return true;
        }

        // KeyCode.getName() rather than the event's character: the gamepad layer synthesises key
        // presses carrying no character at all, so reading one would break controller input. The
        // single-character check drops the numpad, whose names read "Numpad 4".
        String key = code.getName();
        boolean typeable = (code.isLetterKey() || code.isDigitKey()) && key.length() == 1;
        if (!typeable) {
            return false;
        }
        if (name.length() < Pilots.MAX_NAME_LENGTH) {
            name.append(key.toUpperCase());
        }
        return true;
    }

    private void refresh() {
        for (int seat = 0; seat < SEATS; seat++) {
            String name = typed.get(seat).toString();
            String shown = name.isEmpty() ? pilots.name(seat + 1) : name;
            rows.get(seat).setText("PLAYER " + (seat + 1) + "        " + shown);
        }

        int focused = navigator.focusedIndex();
        if (focused >= SEATS) {
            standing.setText("Type to rename    Backspace to delete");
            return;
        }
        String name = typed.get(focused).toString();
        if (name.isEmpty()) {
            standing.setText("Type a name to replace the default");
            return;
        }
        int career = pilots.careerScore(name);
        Rank rank = Rank.forCareerScore(career);
        standing.setText(rank.label() + "    career " + career);
    }

    /** Writes both names back on the way out, so a half-typed name is never saved mid-edit. */
    private void commit() {
        for (int seat = 0; seat < SEATS; seat++) {
            pilots.setName(seat + 1, typed.get(seat).toString());
        }
    }
}
