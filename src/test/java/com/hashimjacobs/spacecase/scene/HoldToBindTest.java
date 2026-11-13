package com.hashimjacobs.spacecase.scene;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.scene.HoldToBind.Outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HoldToBindTest {

    private static final long SECOND = 1_000_000_000L;

    private final HoldToBind prompt = new HoldToBind();
    private PadButton down;

    private Outcome at(double seconds) {
        return prompt.tick((long) (seconds * SECOND), button -> button == down);
    }

    @Test
    void itCountsDownFromEightAndGivesUp() {
        assertEquals(Outcome.WAITING, at(0));
        assertEquals(8, prompt.secondsLeft());
        at(0.5);
        assertEquals(8, prompt.secondsLeft(), "the eight stays up for its whole second");
        at(1.5);
        assertEquals(7, prompt.secondsLeft());
        assertEquals(Outcome.WAITING, at(7.9));
        assertEquals(1, prompt.secondsLeft());
        assertEquals(Outcome.TIMED_OUT, at(8.0));
    }

    @Test
    void aButtonBindsOnlyAfterAFullSecondHeld() {
        at(0);
        down = PadButton.X;
        prompt.pressed(PadButton.X);
        assertEquals(Outcome.WAITING, at(0.5));
        assertEquals(0.5, prompt.progress(), 1e-9);
        assertEquals(Outcome.WAITING, at(0.99));
        assertEquals(Outcome.BOUND, at(1.0));
        assertEquals(PadButton.X, prompt.candidate());
    }

    @Test
    void lettingGoEarlyEmptiesTheWheelAndTheWaitCarriesOn() {
        at(0);
        down = PadButton.X;
        prompt.pressed(PadButton.X);
        at(0.9);
        down = null;
        assertEquals(Outcome.WAITING, at(1.0));
        assertNull(prompt.candidate());
        assertEquals(0, prompt.progress(), 1e-9);

        // A second go has to earn its own full second, not finish the first one's.
        down = PadButton.X;
        prompt.pressed(PadButton.X);
        assertEquals(Outcome.WAITING, at(1.9));
        assertEquals(Outcome.BOUND, at(2.0));
    }

    @Test
    void changingButtonsMidHoldStartsTheHoldAgain() {
        at(0);
        down = PadButton.X;
        prompt.pressed(PadButton.X);
        at(0.9);
        down = PadButton.Y;
        prompt.pressed(PadButton.Y);
        assertEquals(Outcome.WAITING, at(1.5));
        assertEquals(Outcome.BOUND, at(1.9));
        assertEquals(PadButton.Y, prompt.candidate());
    }

    /** Otherwise a hold begun with half a second left would be cut off before it could finish. */
    @Test
    void theCountdownStandsStillWhileAButtonIsHeld() {
        at(0);
        at(7.5);
        down = PadButton.A;
        prompt.pressed(PadButton.A);
        assertEquals(Outcome.WAITING, at(8.4));
        assertEquals(Outcome.BOUND, at(8.5));
    }
}
