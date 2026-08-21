package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;

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

    public record KeyChange(KeyCode code, boolean pressed) {
    }

    private final KeyCode up;
    private final KeyCode down;
    private final KeyCode left;
    private final KeyCode right;
    private final KeyCode fire;
    private final KeyCode confirm;

    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    private final Set<PadButton> previousButtons = EnumSet.noneOf(PadButton.class);
    private int polls;

    /**
     * Slot 0 drives player one and slot 1 player two, using each player's existing keys from
     * {@link PlayerControls} so the pad and the keyboard are interchangeable mid-game.
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
        boolean playerOne = slot == 0;
        up = playerOne ? KeyCode.W : KeyCode.UP;
        down = playerOne ? KeyCode.S : KeyCode.DOWN;
        left = playerOne ? KeyCode.A : KeyCode.LEFT;
        right = playerOne ? KeyCode.D : KeyCode.RIGHT;
        fire = playerOne ? KeyCode.SHIFT : KeyCode.COMMA;
        confirm = playerOne ? KeyCode.SPACE : KeyCode.ENTER;
    }

    /** Returns the changes to apply, in the order they must be applied. */
    public List<KeyChange> poll(PadState state, double deadzone, PadButton fireButton,
                                PadButton pauseButton) {
        if (!state.connected()) {
            List<KeyChange> releases = releaseEverything();
            return releases;
        }

        Set<KeyCode> wanted = wantedKeys(state, deadzone, fireButton);
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
        List<KeyChange> changes = new ArrayList<>();
        for (KeyCode code : held) {
            changes.add(new KeyChange(code, false));
        }
        held.clear();
        previousButtons.clear();
        return changes;
    }

    private Set<KeyCode> wantedKeys(PadState state, double deadzone, PadButton fireButton) {
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
        if (state.isPressed(fireButton)) {
            wanted.add(fire);
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
