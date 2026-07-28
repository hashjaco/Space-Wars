package com.hashimjacobs.spacecase.engine;

/**
 * Converts wall-clock frame timestamps into a fixed number of simulation steps.
 *
 * The loop used to ignore the frame timestamp entirely and move everything a fixed amount per
 * frame, so the game ran at whatever rate the display refreshed -- roughly double speed on a 120Hz
 * panel compared with 60Hz. Accumulating real elapsed time and stepping at a fixed rate decouples
 * simulation speed from refresh rate.
 *
 * Kept free of JavaFX so the timing behaviour is unit testable.
 */
public final class FixedTimestep {

    public static final int STEPS_PER_SECOND = 60;
    private static final long NANOS_PER_STEP = 1_000_000_000L / STEPS_PER_SECOND;

    /**
     * Upper bound on steps produced from one frame. Without it, a long stall (a breakpoint, a
     * window drag, the machine sleeping) would bank seconds of debt and the game would fast-forward
     * through it the instant it resumed.
     */
    private static final int MAX_STEPS_PER_FRAME = 5;

    private long previousFrameNanos;
    private long accumulatedNanos;
    private boolean started;

    /**
     * Steps owed for the frame at {@code frameNanos}. The first call establishes the baseline and
     * returns zero, since there is no previous frame to measure against.
     */
    public int stepsFor(long frameNanos) {
        if (!started) {
            started = true;
            previousFrameNanos = frameNanos;
            return 0;
        }

        long elapsed = frameNanos - previousFrameNanos;
        previousFrameNanos = frameNanos;
        // A non-monotonic or repeated timestamp must not wind the accumulator backwards.
        if (elapsed < 0) {
            return 0;
        }

        accumulatedNanos += elapsed;
        int steps = (int) (accumulatedNanos / NANOS_PER_STEP);
        if (steps <= 0) {
            return 0;
        }

        if (steps > MAX_STEPS_PER_FRAME) {
            steps = MAX_STEPS_PER_FRAME;
            accumulatedNanos = 0;
            return steps;
        }
        accumulatedNanos -= (long) steps * NANOS_PER_STEP;
        return steps;
    }

    /**
     * Drops banked time and forgets the previous frame. Call when resuming from a pause, so the
     * paused interval is not replayed as simulation debt.
     */
    public void reset() {
        started = false;
        accumulatedNanos = 0;
        previousFrameNanos = 0;
    }
}
