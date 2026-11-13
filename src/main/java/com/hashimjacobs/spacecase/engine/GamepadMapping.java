package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;

/**
 * Turns one gamepad's state into the key presses and releases that drive the game.
 *
 * The whole controller feature rests on this translation: the rest of the engine only ever asks
 * {@link InputState#isHeld} and {@link com.hashimjacobs.spacecase.scene.MenuNavigator#handleKey}
 * about {@link KeyCode}s, so a pad that speaks in key codes needs no other integration. One instance
 * per pad, holding the state needed to spot changes between polls.
 *
 * Pure: no native calls and no Scene, so the rules below are unit-tested without hardware.
 */
public final class GamepadMapping {

    /**
     * The release threshold as a fraction of the press threshold. A stick resting fractionally off
     * centre sits on a single threshold and chatters its direction on and off every frame; needing
     * a clearly smaller reading to let go stops that. Expressed as a ratio rather than a subtraction
     * so it cannot fall to zero at a small deadzone and latch the direction on forever.
     */
    private static final double RELEASE_RATIO = 0.6;

    /**
     * Held keys are re-sent every this many polls. {@link InputState#setMenuRouter} clears the held
     * set, so after unpausing with the stick still pushed a purely edge-driven pad would leave the
     * ship frozen until the player re-centred it. Repeating also gives menus stick auto-repeat.
     * Confirm and pause are excluded -- repeating those would re-activate a menu item several times
     * a second.
     */
    private static final int REPEAT_EVERY_POLLS = 8;

    /** A key going down or coming up. Ordering within a poll matters, so these are a list. */
    /**
     * The key the pad's cancel button speaks, so a menu can treat B as "back".
     *
     * Deliberately not {@link KeyCode#ESCAPE}. Escape is what a menu already means by back, so
     * sending it would have been one line -- but gameplay reads Escape as pause, and the button
     * immediately next to fire must not pause a fight when it is mashed. A code no keyboard
     * produces keeps the two apart: menus opt into it, gameplay lets it fall through to the held
     * set where nothing reads it.
     *
     * Not {@code BACK_SPACE} either, which name entry already spends on deleting a letter.
     */
    public static final KeyCode MENU_CANCEL = KeyCode.CANCEL;

    /**
     * What the stick and d-pad speak: four codes per pad that no keyboard sends, in the order up,
     * down, left, right.
     *
     * They used to speak each seat's default movement keys, which worked until a pilot rebound
     * those keys -- the pad went on pressing W, and W no longer meant up. A pad direction is not a
     * keyboard key and must not depend on one. {@link PlayerControls} adds these to each seat's
     * directions, so flight, the garage and the stage on foot all follow the stick whatever the
     * keyboard is bound to; {@link MenuRepeat} offers a menu the matching arrow key.
     */
    private static final KeyCode[][] DIRECTIONS = {
            {KeyCode.SOFTKEY_0, KeyCode.SOFTKEY_1, KeyCode.SOFTKEY_2, KeyCode.SOFTKEY_3},
            {KeyCode.SOFTKEY_4, KeyCode.SOFTKEY_5, KeyCode.SOFTKEY_6, KeyCode.SOFTKEY_7}};
    private static final KeyCode[] ARROWS = {KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT};

    /**
     * The code this player's pad sends for a direction, or null for an action that is not one.
     *
     * @param player one or two
     */
    public static KeyCode direction(int player, ControlAction action) {
        return action.ordinal() < ARROWS.length ? DIRECTIONS[player - 1][action.ordinal()] : null;
    }

    /** The arrow key a menu should read a pad direction as; any other code comes back unchanged. */
    public static KeyCode menuKey(KeyCode code) {
        for (KeyCode[] pad : DIRECTIONS) {
            for (int way = 0; way < pad.length; way++) {
                if (pad[way] == code) {
                    return ARROWS[way];
                }
            }
        }
        return code;
    }

    public record KeyChange(KeyCode code, boolean pressed) {
    }

    private final KeyCode up;
    private final KeyCode down;
    private final KeyCode left;
    private final KeyCode right;
    private final KeyCode confirm;

    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    private final Set<PadButton> previousButtons = EnumSet.noneOf(PadButton.class);
    private int polls;

    /**
     * Slot 0 drives player one and slot 1 player two. Buttons hold down that player's own keys, as
     * bound at the moment of the poll, so the pad and the keyboard are interchangeable mid-game.
     *
     * The fire button yields two keys: the player's fire key, which is held, plus a menu confirm
     * key, which is tapped. That is what lets one button both shoot and choose a menu item without
     * this class needing to know which screen is up.
     *
     * ponytail: both slots therefore drive the same menu cursor, since MenuNavigator answers to
     * either player's keys. That is inherent to speaking in key codes, and is what keeps the whole
     * feature to one integration point; it is no worse than two people sharing a keyboard. Give
     * menus a notion of which pad owns the cursor only if players complain.
     */
    public GamepadMapping(int slot) {
        up = DIRECTIONS[slot][0];
        down = DIRECTIONS[slot][1];
        left = DIRECTIONS[slot][2];
        right = DIRECTIONS[slot][3];
        // Enter for both pads, never Space: Space is also a lone player one's second fire key, and
        // in the garage fire buys -- so a Space tap riding on the fire button bought two levels
        // with every press. Enter is nobody's fire key, and the garage ignores it on purpose.
        confirm = KeyCode.ENTER;
    }

    /**
     * Returns the changes to apply, in the order they must be applied.
     *
     * @param buttons which button this pad has each action on
     * @param keys    which key this player has each keyboard action on; a held button holds that
     *                key, so the pad follows a keyboard rebind instead of being broken by it
     */
    public List<KeyChange> poll(PadState state, double deadzone,
                                Function<PadAction, PadButton> buttons,
                                Function<ControlAction, KeyCode> keys) {
        if (!state.connected()) {
            List<KeyChange> releases = releaseEverything();
            return releases;
        }
        PadButton fireButton = buttons.apply(PadAction.FIRE);
        PadButton pauseButton = buttons.apply(PadAction.PAUSE);

        Set<KeyCode> wanted = wantedKeys(state, deadzone, buttons, keys);
        List<KeyChange> changes = new ArrayList<>();

        for (KeyCode code : held) {
            if (!wanted.contains(code)) {
                changes.add(new KeyChange(code, false));
            }
        }

        polls++;
        boolean repeating = polls % REPEAT_EVERY_POLLS == 0;
        for (KeyCode code : wanted) {
            if (repeating || !held.contains(code)) {
                changes.add(new KeyChange(code, true));
            }
        }

        // Tapped rather than held: the press is what a menu reacts to, and the release stops the key
        // sticking in the held set once the menu has gone.
        if (justPressed(state, fireButton)) {
            changes.add(new KeyChange(confirm, true));
            changes.add(new KeyChange(confirm, false));
        }
        if (justPressed(state, pauseButton)) {
            changes.add(new KeyChange(KeyCode.ESCAPE, true));
            changes.add(new KeyChange(KeyCode.ESCAPE, false));
        }
        // B means back, which is what every console has spent thirty years teaching. Hardcoded
        // rather than bindable because it is a convention rather than a preference -- but an
        // explicit binding still wins, so a player who has put fire or pause on B gets what they
        // asked for instead of both at once.
        if (fireButton != PadButton.B && pauseButton != PadButton.B
                && justPressed(state, PadButton.B)) {
            changes.add(new KeyChange(MENU_CANCEL, true));
            changes.add(new KeyChange(MENU_CANCEL, false));
        }

        held.clear();
        held.addAll(wanted);
        previousButtons.clear();
        previousButtons.addAll(state.pressed());
        return changes;
    }

    /**
     * Lets go of everything this pad was holding. A pad whose battery dies mid-thrust must not leave
     * the ship drifting, the same hazard the focus-loss guard in {@link InputState} protects against.
     */
    public List<KeyChange> releaseEverything() {
        List<KeyChange> changes = releaseKeys();
        previousButtons.clear();
        return changes;
    }

    /**
     * Lets go of the keys but remembers which buttons are down, for the moment the controller
     * screen starts listening: the button that chose the row is still held, and forgetting it would
     * make it look newly pressed and bind it on the spot.
     */
    public List<KeyChange> releaseKeys() {
        List<KeyChange> changes = new ArrayList<>();
        for (KeyCode code : held) {
            changes.add(new KeyChange(code, false));
        }
        held.clear();
        return changes;
    }

    /**
     * The button that has just gone down, or null. Stands in for {@link #poll} while the controller
     * screen is waiting for a button, and sends no keys -- otherwise the button being bound would
     * also confirm, cancel or pause on its way past.
     */
    public PadButton capture(PadState state) {
        PadButton fresh = null;
        // In enum order rather than the set's, so two buttons landing in one poll bind the same
        // one every time.
        for (PadButton button : PadButton.values()) {
            if (fresh == null && justPressed(state, button)) {
                fresh = button;
            }
        }
        previousButtons.clear();
        previousButtons.addAll(state.pressed());
        return fresh;
    }

    private Set<KeyCode> wantedKeys(PadState state, double deadzone,
                                    Function<PadAction, PadButton> buttons,
                                    Function<ControlAction, KeyCode> keys) {
        Set<KeyCode> wanted = EnumSet.noneOf(KeyCode.class);
        if (state.dpadUp() || pushedPast(-state.leftStickY(), deadzone, up)) {
            wanted.add(up);
        }
        if (state.dpadDown() || pushedPast(state.leftStickY(), deadzone, down)) {
            wanted.add(down);
        }
        if (state.dpadLeft() || pushedPast(-state.leftStickX(), deadzone, left)) {
            wanted.add(left);
        }
        if (state.dpadRight() || pushedPast(state.leftStickX(), deadzone, right)) {
            wanted.add(right);
        }
        // Always sent, whatever screen is up: nothing in flight reads the on-foot keys, and the
        // stage on foot reads nothing else. That is what saves the pad layer from knowing which one
        // is running.
        for (PadAction action : PadAction.values()) {
            if (action.key() != null && state.isPressed(buttons.apply(action))) {
                wanted.add(keys.apply(action.key()));
            }
        }
        return wanted;
    }

    private boolean pushedPast(double travel, double deadzone, KeyCode code) {
        double threshold = held.contains(code) ? deadzone * RELEASE_RATIO : deadzone;
        boolean past = travel >= threshold;
        return past;
    }

    private boolean justPressed(PadState state, PadButton button) {
        boolean rising = state.isPressed(button) && !previousButtons.contains(button);
        return rising;
    }
}
