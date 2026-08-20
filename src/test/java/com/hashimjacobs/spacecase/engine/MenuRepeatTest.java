package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auto-repeat for menus: one move, a pause, then a steady walk.
 *
 * The clock is handed in, so the timings are exercised exactly and instantly rather than by sleeping.
 * KeyCode is a plain enum and the gate touches no toolkit, so this runs anywhere.
 */
class MenuRepeatTest {

    private static final long MS = 1_000_000L;

    /** What the gamepad does: re-sends a held key roughly every eight polls at sixty frames. */
    private static final long PAD_REPEAT_MS = 133;

    @Test
    void theFirstPressAlwaysGetsThrough() {
        Gate gate = new Gate();

        assertTrue(gate.press(KeyCode.DOWN));
        assertEquals(List.of(KeyCode.DOWN), gate.seen);
    }

    @Test
    void aHeldKeyIsSwallowedUntilTheFirstWaitHasPassed() {
        Gate gate = new Gate();
        gate.press(KeyCode.DOWN);

        for (long at = PAD_REPEAT_MS; at < 500; at += PAD_REPEAT_MS) {
            gate.at(at);
            gate.press(KeyCode.DOWN);
        }
        assertEquals(1, gate.seen.size(), "nothing may move during the first wait");

        gate.at(532);
        gate.press(KeyCode.DOWN);
        assertEquals(2, gate.seen.size(), "the walk starts once the wait is over");
    }

    /**
     * The schedule is fixed, not measured from each press.
     *
     * The pad only reports every 133ms, so timing repeats from arrival would round every wait up by
     * most of a report -- turning the advertised three moves a second into barely two. Stepping in
     * pad-sized ticks is what makes that regression visible here.
     */
    @Test
    void theWalkKeepsToItsScheduleRatherThanDriftingWithTheReports() {
        Gate gate = new Gate();
        gate.press(KeyCode.DOWN);
        for (long at = PAD_REPEAT_MS; at <= 2000; at += PAD_REPEAT_MS) {
            gate.at(at);
            gate.press(KeyCode.DOWN);
        }

        // 500ms wait, then one every 350ms: moves at 0, ~500, ~850, ~1200, ~1550, ~1900.
        assertEquals(6, gate.seen.size(),
                "expected six moves in two seconds of holding, got " + gate.seen.size());
    }

    /**
     * The one that matters: tapping as fast as a thumb can go must never drop a press.
     *
     * An earlier version guessed at releases from the gap between presses, so anything quicker than
     * a fifth of a second read as a held key and the second tap vanished. Real releases are reported
     * now, and this walks far faster than that old window to prove none of them go missing.
     */
    @Test
    void fastTappingNeverDropsAPress() {
        Gate gate = new Gate();

        for (int tap = 0; tap < 10; tap++) {
            gate.at(tap * 40);
            gate.press(KeyCode.DOWN);
            gate.release(KeyCode.DOWN);
        }

        assertEquals(10, gate.seen.size(), "every deliberate tap must move exactly one row");
    }

    @Test
    void aReleaseMakesTheNextPressImmediate() {
        Gate gate = new Gate();
        gate.press(KeyCode.DOWN);
        gate.at(PAD_REPEAT_MS);
        assertFalse(gate.moved(KeyCode.DOWN), "still held, still inside the first wait");

        gate.release(KeyCode.DOWN);
        assertTrue(gate.moved(KeyCode.DOWN), "a fresh push must not wait out the old schedule");
    }

    /** A window losing focus mid-press leaves no release behind, so the gate is told to forget. */
    @Test
    void clearingForgetsAHeldKey() {
        Gate gate = new Gate();
        gate.press(KeyCode.DOWN);
        gate.at(PAD_REPEAT_MS);
        assertFalse(gate.moved(KeyCode.DOWN));

        gate.clear();
        assertTrue(gate.moved(KeyCode.DOWN), "after focus returns the key is not still held");
    }

    @Test
    void reversingDirectionRespondsAtOnce() {
        Gate gate = new Gate();
        gate.press(KeyCode.DOWN);

        gate.at(PAD_REPEAT_MS);
        assertTrue(gate.moved(KeyCode.UP), "changing your mind must not wait out the other key");
    }

    /** Both pilots shop at once, so one player's held key must not gate the other's. */
    @Test
    void twoKeysHeldAtOnceThrottleIndependently() {
        Gate gate = new Gate();
        gate.press(KeyCode.S);
        gate.press(KeyCode.DOWN);
        assertEquals(2, gate.seen.size(), "each player's first press stands on its own");

        gate.at(PAD_REPEAT_MS);
        gate.press(KeyCode.S);
        gate.press(KeyCode.DOWN);
        assertEquals(2, gate.seen.size(), "and each is then held off on its own clock");
    }

    @Test
    void keysThatAreNotDirectionsAreNeverThrottled() {
        Gate gate = new Gate();

        for (KeyCode code : List.of(KeyCode.ENTER, KeyCode.SPACE, KeyCode.ESCAPE, KeyCode.Q)) {
            gate.press(code);
            gate.press(code);
        }

        assertEquals(8, gate.seen.size(), "activation and typing are one-shot already");
    }

    /**
     * A swallowed press still has to report itself consumed.
     *
     * Answering false would drop the key into {@link InputState}'s held set, where it would fly the
     * ship around underneath the pause overlay the player is reading.
     */
    @Test
    void aSwallowedPressAnswersAsTheMenuDid() {
        Gate gate = new Gate();
        assertTrue(gate.press(KeyCode.DOWN), "the menu consumes its navigation keys");

        gate.at(PAD_REPEAT_MS);
        assertTrue(gate.press(KeyCode.DOWN),
                "a swallowed repeat must stay consumed, or it reaches the ship");
    }

    @Test
    void aSwallowedPressStaysUnconsumedWhenTheMenuDidNotWantIt() {
        Gate gate = new Gate(code -> false);
        assertFalse(gate.press(KeyCode.LEFT));

        gate.at(PAD_REPEAT_MS);
        assertFalse(gate.press(KeyCode.LEFT), "a key the menu ignores must keep travelling");
    }

    /** A menu router that consumes everything, wrapped in a gate on a clock we drive by hand. */
    private static final class Gate {
        private final List<KeyCode> seen = new ArrayList<>();
        private final MenuRepeat gate;
        private long nanos;

        Gate() {
            this(null);
        }

        Gate(Predicate<KeyCode> answer) {
            this.gate = new MenuRepeat(code -> {
                seen.add(code);
                return answer == null || answer.test(code);
            }, () -> nanos);
        }

        void at(long milliseconds) {
            nanos = milliseconds * MS;
        }

        void release(KeyCode code) {
            gate.release(code);
        }

        void clear() {
            gate.clear();
        }

        boolean press(KeyCode code) {
            return gate.test(code);
        }

        /** Whether this press reached the menu, rather than what the menu answered. */
        boolean moved(KeyCode code) {
            int before = seen.size();
            gate.test(code);
            return seen.size() > before;
        }
    }
}
