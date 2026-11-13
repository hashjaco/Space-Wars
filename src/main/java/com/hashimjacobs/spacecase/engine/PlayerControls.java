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
        Set<KeyCode> fire,
        Set<KeyCode> special) {

    /** A seat with no special key, which is every caller from before the special. */
    public PlayerControls(Set<KeyCode> up, Set<KeyCode> down, Set<KeyCode> left, Set<KeyCode> right,
                          Set<KeyCode> fire) {
        this(up, down, left, right, fire, Set.of());
    }

    /** How many seats {@link Settings} holds bindings for. One keyboard carries two. */
    private static final int SEATS_WITH_KEYS = 2;

    /**
     * This player's bound keys, plus the alternates the game has always also accepted.
     *
     * @param player one or two
     * @param solo   whether player one is flying alone, which is what opens the arrows to them
     */
    /**
     * A seat with nothing bound, for a ship this machine does not fly.
     *
     * An online room seats four; {@link Settings} binds two, because two is what one keyboard can
     * carry. A networked game takes every ship's input from the wire rather than from
     * {@link ShipController#sample}, so the far seats need no keys -- and giving them an empty set
     * is closer to the truth than inventing bindings nobody can press.
     */
    public static PlayerControls none() {
        return new PlayerControls(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static PlayerControls of(Settings settings, int player, boolean solo) {
        if (player > SEATS_WITH_KEYS) {
            return none();
        }
        return new PlayerControls(
                keys(settings, player, solo, ControlAction.UP),
                keys(settings, player, solo, ControlAction.DOWN),
                keys(settings, player, solo, ControlAction.LEFT),
                keys(settings, player, solo, ControlAction.RIGHT),
                keys(settings, player, solo, ControlAction.FIRE),
                special(settings, player, solo));
    }

    /**
     * The ship special: the bound Special key (which is also where a pad's B lands, see
     * GamepadMapping), plus Z for player one and / for player two -- both, for player one alone.
     * Fixed extras like Space is for fire, so a rebind never takes them away. / is player two's
     * Punch, but only on foot, which never reads these.
     */
    private static Set<KeyCode> special(Settings settings, int player, boolean solo) {
        java.util.Set<KeyCode> keys = new java.util.HashSet<>();
        keys.add(settings.key(player, ControlAction.SPECIAL));
        keys.add(ControlAction.SPECIAL.defaultKey(player));
        if (player == 1) {
            keys.add(KeyCode.Z);
        }
        if (player == 2 || solo) {
            keys.add(KeyCode.SLASH);
        }
        return Set.copyOf(keys);
    }

    private static Set<KeyCode> keys(Settings settings, int player, boolean solo,
                                     ControlAction action) {
        // A set, so the alternate a pilot has bound outright is simply not there twice.
        Set<KeyCode> keys = new java.util.HashSet<>();
        keys.add(settings.key(player, action));
        KeyCode alternate = action.alternate(player, solo);
        if (alternate != null) {
            keys.add(alternate);
        }
        // The pad's stick and d-pad, which answer to no keyboard key and so survive any rebind.
        KeyCode pad = GamepadMapping.direction(player, action);
        if (pad != null) {
            keys.add(pad);
        }
        return Set.copyOf(keys);
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
