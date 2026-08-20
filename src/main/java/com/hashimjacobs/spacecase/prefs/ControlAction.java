package com.hashimjacobs.spacecase.prefs;

import javafx.scene.input.KeyCode;

/**
 * Something a pilot can do at the keyboard, and the key that does it by default.
 *
 * Exists so a binding is addressed by what it does rather than by a field name, which is what keeps
 * {@link Settings} to one array instead of five and lets the controls screen build its rows from a
 * loop. The same shape {@link PadButton} gives the gamepad.
 *
 * <p>Bindings are per player and hold one key each. The extra keys the game has always accepted --
 * the arrows and Space for a solo player one, the full stop for player two's trigger -- are not
 * bindings and are not rebindable; see {@link #alternate}. They are conveniences layered on top, and
 * folding them into the stored binding would mean a rebind silently taking one of them away.
 */
public enum ControlAction {

    UP("Up", KeyCode.W, KeyCode.UP),
    DOWN("Down", KeyCode.S, KeyCode.DOWN),
    LEFT("Left", KeyCode.A, KeyCode.LEFT),
    RIGHT("Right", KeyCode.D, KeyCode.RIGHT),
    FIRE("Fire", KeyCode.SHIFT, KeyCode.COMMA);

    private final String label;
    private final KeyCode playerOneDefault;
    private final KeyCode playerTwoDefault;

    ControlAction(String label, KeyCode playerOneDefault, KeyCode playerTwoDefault) {
        this.label = label;
        this.playerOneDefault = playerOneDefault;
        this.playerTwoDefault = playerTwoDefault;
    }

    public String label() {
        return label;
    }

    /** @param player one or two */
    public KeyCode defaultKey(int player) {
        return player == 1 ? playerOneDefault : playerTwoDefault;
    }

    /**
     * A key that keeps working alongside the bound one, or null where there is none.
     *
     * Player one flying alone has always answered to the arrows and Space as well as to WASD and
     * Shift, and player two's trigger has always taken the full stop as well as the comma. Both
     * survive rebinding untouched, so a pilot who never opens the controls screen notices nothing.
     *
     * @param player one or two
     * @param solo   whether player one is flying without a second seat
     */
    public KeyCode alternate(int player, boolean solo) {
        if (player == 2) {
            return this == FIRE ? KeyCode.PERIOD : null;
        }
        if (!solo) {
            return null;
        }
        return this == FIRE ? KeyCode.SPACE : playerTwoDefault;
    }
}
