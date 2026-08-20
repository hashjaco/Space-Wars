package com.hashimjacobs.spacecase.engine;

import java.util.Set;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * Key bindings for one player. Each direction accepts a set so single-player can drive with either
 * WASD or the arrow keys.
 *
 * Built from {@link Settings} rather than from constants, since the bound half is a pilot's to
 * change; {@link ControlAction} holds the defaults and the fixed alternates.
 */
public record PlayerControls(
        Set<KeyCode> up,
        Set<KeyCode> down,
        Set<KeyCode> left,
        Set<KeyCode> right,
        Set<KeyCode> fire) {

    /**
     * This player's bound keys, plus the alternates the game has always also accepted.
     *
     * @param player one or two
     * @param solo   whether player one is flying alone, which is what opens the arrows to them
     */
    public static PlayerControls of(Settings settings, int player, boolean solo) {
        return new PlayerControls(
                keys(settings, player, solo, ControlAction.UP),
                keys(settings, player, solo, ControlAction.DOWN),
                keys(settings, player, solo, ControlAction.LEFT),
                keys(settings, player, solo, ControlAction.RIGHT),
                keys(settings, player, solo, ControlAction.FIRE));
    }

    private static Set<KeyCode> keys(Settings settings, int player, boolean solo,
                                     ControlAction action) {
        KeyCode bound = settings.key(player, action);
        KeyCode alternate = action.alternate(player, solo);
        // The alternate is dropped when a pilot has bound that very key, so the set stays a set and
        // an unbound-looking duplicate never appears.
        return alternate == null || alternate == bound
                ? Set.of(bound)
                : Set.of(bound, alternate);
    }

    public boolean anyHeld(InputState input, Set<KeyCode> codes) {
        for (KeyCode code : codes) {
            if (input.isHeld(code)) {
                return true;
            }
        }
        return false;
    }
}
