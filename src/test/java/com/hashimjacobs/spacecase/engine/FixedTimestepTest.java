package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for display-rate coupling: the loop used to ignore the frame timestamp, so the
 * game ran about twice as fast on a 120Hz display as on a 60Hz one.
 */
class FixedTimestepTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void aRefreshRateChangeDoesNotChangeSimulationSpeed() {
        int at60 = stepsOverOneSecond(60);
        int at120 = stepsOverOneSecond(120);
        int at144 = stepsOverOneSecond(144);

        assertEquals(FixedTimestep.STEPS_PER_SECOND, at60);
        assertEquals(at60, at120, "120Hz must not run the simulation faster than 60Hz");
        assertEquals(at60, at144, "144Hz must not run the simulation faster than 60Hz");
    }

    @Test
    void aSlowDisplayStillAdvancesRealTime() {
        // 30Hz delivers half as many frames, so each frame owes two steps.
        assertEquals(FixedTimestep.STEPS_PER_SECOND, stepsOverOneSecond(30));
    }

    @Test
    void theFirstFrameEstablishesTheBaselineAndCostsNothing() {
        FixedTimestep timestep = new FixedTimestep();
        assertEquals(0, timestep.stepsFor(1234), "nothing has elapsed before the first frame");
    }

    @Test
    void framesShorterThanAStepBankTimeRatherThanLosingIt() {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(0);

        // Ten frames that together span exactly one step's worth of time.
        long oneStep = SECOND / FixedTimestep.STEPS_PER_SECOND;
        int steps = 0;
        for (int i = 1; i <= 10; i++) {
            steps += timestep.stepsFor(i * oneStep / 10);
        }
        assertEquals(1, steps, "sub-step frames must accumulate instead of being discarded");
    }

    @Test
    void aLongStallDoesNotFastForwardTheGame() {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(0);

        // A ten-second freeze, as if paused in a debugger.
        int steps = timestep.stepsFor(10 * SECOND);

        assertTrue(steps <= 5, "a stall must be clamped, not replayed as debt; got " + steps);
    }

    @Test
    void aStallDoesNotLeaveDebtForLaterFrames() {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(0);
        timestep.stepsFor(10 * SECOND);

        long now = 10 * SECOND;
        long frame = SECOND / 60;
        int steps = 0;
        for (int i = 0; i < 60; i++) {
            now += frame;
            steps += timestep.stepsFor(now);
        }
        assertEquals(60, steps, "the clamped frame must not leave banked time behind");
    }

    @Test
    void aRepeatedOrBackwardsTimestampIsIgnored() {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(SECOND);

        assertEquals(0, timestep.stepsFor(SECOND), "a repeated timestamp owes nothing");
        assertEquals(0, timestep.stepsFor(0), "a backwards timestamp must not wind time back");
    }

    @Test
    void resetDropsBankedTime() {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(0);
        timestep.reset();

        assertEquals(0, timestep.stepsFor(5 * SECOND),
                "after reset the next frame is a fresh baseline");
    }

    /**
     * Timestamps are derived from the frame index rather than by summing a truncated frame
     * duration, so a rate like 144Hz that does not divide a second evenly still totals exactly one
     * second -- the same as a real monotonic clock.
     */
    private static int stepsOverOneSecond(int framesPerSecond) {
        FixedTimestep timestep = new FixedTimestep();
        timestep.stepsFor(0);

        int steps = 0;
        for (int frame = 1; frame <= framesPerSecond; frame++) {
            long now = frame * SECOND / framesPerSecond;
            steps += timestep.stepsFor(now);
        }
        return steps;
    }
}
