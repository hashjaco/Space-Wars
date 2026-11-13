package com.hashimjacobs.spacecase.scene;

import java.util.function.Predicate;

import com.hashimjacobs.spacecase.prefs.PadButton;

/**
 * The rules of the bind prompt: a few seconds to pick a button, then a full second holding it.
 *
 * Binding on the first press was too quick to trust. A button brushed on the way to the one that
 * was meant, or the one still being let go of after choosing the row, was bound before the pilot
 * had seen it happen. Holding makes the choice deliberate and gives the screen a second to show
 * which button it thinks is meant; letting go early costs nothing and the wait carries on.
 *
 * Time and buttons are passed in, so this is tested without a clock, a pad or the toolkit.
 */
final class HoldToBind {

    static final long WAIT_NANOS = 8_000_000_000L;
    static final long HOLD_NANOS = 1_000_000_000L;

    enum Outcome { WAITING, BOUND, TIMED_OUT }

    private long last = -1;
    private long waited;
    private long heldFor;
    private PadButton candidate;

    /** A button has just gone down. The newest press is the one being held for, from zero. */
    void pressed(PadButton button) {
        candidate = button;
        heldFor = 0;
    }

    /**
     * @param now  any steadily rising clock, in nanoseconds; the first call only starts it
     * @param down whether a button is down right now
     */
    Outcome tick(long now, Predicate<PadButton> down) {
        long passed = last < 0 ? 0 : now - last;
        last = now;
        if (candidate != null && down.test(candidate)) {
            // The countdown stands still during a hold, so one begun in the last second can finish.
            heldFor += passed;
            return heldFor >= HOLD_NANOS ? Outcome.BOUND : Outcome.WAITING;
        }
        candidate = null;
        heldFor = 0;
        waited += passed;
        return waited >= WAIT_NANOS ? Outcome.TIMED_OUT : Outcome.WAITING;
    }

    /** The button being held, or null while none is. */
    PadButton candidate() {
        return candidate;
    }

    /** Whole seconds left to pick a button, counting 8 down to 1. */
    int secondsLeft() {
        return (int) Math.max(1, Math.ceil((WAIT_NANOS - waited) / 1e9));
    }

    /** How far through the hold, from 0 to 1. */
    double progress() {
        return Math.min(1.0, (double) heldFor / HOLD_NANOS);
    }
}
