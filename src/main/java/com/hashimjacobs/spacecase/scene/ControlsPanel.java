package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * Rebinding the keyboard, one row per action per seat.
 *
 * Only the gamepad was ever rebindable, which is backwards for a game played mostly at the
 * keyboard. A row is armed by choosing it and bound by the next key pressed, rather than by cycling
 * through every {@code KeyCode} the way the pad rows cycle through their handful of buttons.
 *
 * Two keys are never bindable, and both for the same reason -- they are how a pilot gets out of a
 * mess. Escape leaves the screen and pauses the game; F11 is the fullscreen toggle, which the
 * router installs as an event filter and which therefore never reaches this at all.
 */
final class ControlsPanel extends VBox {

    private static final int PLAYERS = 2;

    private final Settings settings;
    private final MenuButton[][] bindingRow =
            new MenuButton[PLAYERS][ControlAction.values().length];
    private final MenuButton noticeRow;
    private final MenuPanel rows;

    /** The row waiting for a key, or null when nothing is armed. */
    private MenuButton armed;
    private int armedPlayer;
    private ControlAction armedAction;
    private String notice = "";

    ControlsPanel(Settings settings, Runnable onBack) {
        this.settings = settings;

        for (int player = 1; player <= PLAYERS; player++) {
            for (ControlAction action : ControlAction.values()) {
                int seat = player;
                MenuButton row = new MenuButton("", () -> arm(seat, action));
                bindingRow[player - 1][action.ordinal()] = row;
            }
        }
        MenuButton resetRow = new MenuButton("Reset to defaults", this::resetAll);
        // Not a control: it carries whatever the last attempt has to say, and says nothing until
        // something goes wrong. Left navigable so a pad user cannot scroll past it unread.
        noticeRow = new MenuButton("", () -> { });
        MenuButton backRow = new MenuButton("Back", () -> {
            disarm();
            settings.save();
            onBack.run();
        });

        refreshLabels();

        rows = new MenuPanel(flatten(resetRow, noticeRow, backRow));
        setAlignment(Pos.CENTER);
        getChildren().add(rows);
    }

    private MenuButton[] flatten(MenuButton... trailing) {
        MenuButton[] all = new MenuButton[PLAYERS * ControlAction.values().length + trailing.length];
        int at = 0;
        for (int player = 0; player < PLAYERS; player++) {
            for (ControlAction action : ControlAction.values()) {
                all[at++] = bindingRow[player][action.ordinal()];
            }
        }
        for (MenuButton row : trailing) {
            all[at++] = row;
        }
        return all;
    }

    MenuNavigator navigator(Runnable onBack) {
        MenuNavigator navigator = rows.navigator();
        navigator.setOnBack(() -> {
            // Escape backs out of an armed row first, so a pilot who armed one by mistake is not
            // forced to spend a key on it.
            if (armed != null) {
                disarm();
                refreshLabels();
                return;
            }
            settings.save();
            onBack.run();
        });
        return navigator;
    }

    /**
     * Swallows the key that a waiting row is waiting for.
     *
     * Ahead of the navigator, so arming a row and pressing W binds W rather than walking the menu.
     *
     * @return true when the press was consumed as a binding
     */
    boolean captureKey(KeyCode code) {
        if (armed == null || code == KeyCode.ESCAPE) {
            return false;
        }
        ControlAction clash = settings.keyClash(armedPlayer, armedAction, code);
        if (clash != null) {
            notice = code.getName() + " is already " + clash.label();
        } else {
            settings.setKey(armedPlayer, armedAction, code);
            notice = "";
        }
        disarm();
        refreshLabels();
        return true;
    }

    private void arm(int player, ControlAction action) {
        armed = bindingRow[player - 1][action.ordinal()];
        armedPlayer = player;
        armedAction = action;
        notice = "";
        refreshLabels();
    }

    private void disarm() {
        armed = null;
        armedAction = null;
    }

    private void resetAll() {
        for (int player = 1; player <= PLAYERS; player++) {
            settings.resetKeys(player);
        }
        disarm();
        notice = "";
        refreshLabels();
    }

    private void refreshLabels() {
        for (int player = 1; player <= PLAYERS; player++) {
            for (ControlAction action : ControlAction.values()) {
                MenuButton row = bindingRow[player - 1][action.ordinal()];
                String bound = row == armed
                        ? "press a key"
                        : settings.key(player, action).getName();
                row.setText("P" + player + " " + pad(action.label()) + bound);
            }
        }
        noticeRow.setText(notice);
    }

    /** Column alignment without a monospaced font: the labels are all short and known. */
    private static String pad(String label) {
        StringBuilder padded = new StringBuilder(label);
        while (padded.length() < 8) {
            padded.append(' ');
        }
        return padded.toString();
    }
}
