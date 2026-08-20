package com.hashimjacobs.spacecase.engine;

/**
 * How one player's stick should feel this frame, read fresh from the settings each step.
 *
 * Grouped rather than passed as three loose arguments because two of them are unrelated doubles in
 * overlapping ranges: swapping the deadzone and the sensitivity would compile and would only show up
 * as a ship that handles wrong.
 *
 * @param deadzone    travel to ignore around centre, so a worn stick does not drift
 * @param analog      false pins the pad to the digital directions it also emits as key events
 * @param sensitivity above one reaches full speed sooner, below one holds back for finer control
 */
public record StickTuning(double deadzone, boolean analog, double sensitivity) {

    /** Keyboard-only play: nothing to read, so the stick path is skipped entirely. */
    public static StickTuning none() {
        return new StickTuning(0, false, 1);
    }
}
