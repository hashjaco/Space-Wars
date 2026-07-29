package com.hashimjacobs.spacecase.engine;

import java.util.EnumSet;
import java.util.Set;

import com.hashimjacobs.spacecase.prefs.PadButton;

/**
 * One frame of one gamepad, as plain data.
 *
 * Deliberately not SDL's own state object: keeping the snapshot toolkit- and native-free is what
 * lets {@link GamepadMapping} be tested without a controller plugged in, the same trick
 * {@link com.hashimjacobs.spacecase.scene.MenuNavigator} uses to stay testable without the JavaFX
 * toolkit.
 *
 * @param leftStickY positive is downward, following SDL's axis convention rather than screen-up.
 */
public record PadState(
        boolean connected,
        double leftStickX,
        double leftStickY,
        boolean dpadUp,
        boolean dpadDown,
        boolean dpadLeft,
        boolean dpadRight,
        Set<PadButton> pressed) {

    public PadState {
        pressed = Set.copyOf(pressed);
    }

    public static PadState disconnected() {
        PadState state = new PadState(false, 0, 0, false, false, false, false,
                EnumSet.noneOf(PadButton.class));
        return state;
    }

    public boolean isPressed(PadButton button) {
        boolean down = pressed.contains(button);
        return down;
    }
}
