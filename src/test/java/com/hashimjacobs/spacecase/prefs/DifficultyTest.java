package com.hashimjacobs.spacecase.prefs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyTest {

    /**
     * The one value that must not drift: the opening fight is the most-played minute of the game
     * and was tuned by hand against the authored boss numbers.
     */
    @Test
    void theFirstFlagshipOnNormalIsExactlyAsAuthored() {
        assertEquals(1.0, Difficulty.NORMAL.bossScale(1, 1), 1e-9);
    }

    @Test
    void flagshipsGetTougherDeeperIntoARun() {
        double first = Difficulty.NORMAL.bossScale(1, 1);
        double last = Difficulty.NORMAL.bossScale(8, 1);

        assertTrue(last > first, "level eight should be harder than level one");
    }

    /** Without this the run goes flat: level one comes round again identical to the first pass. */
    @Test
    void flagshipsGetTougherOnEveryLoop() {
        double firstPass = Difficulty.NORMAL.bossScale(1, 1);
        double secondPass = Difficulty.NORMAL.bossScale(1, 2);
        double thirdPass = Difficulty.NORMAL.bossScale(1, 3);

        assertTrue(secondPass > firstPass);
        assertTrue(thirdPass > secondPass);
    }

    @Test
    void aLoopOutweighsALevel() {
        // Coming back round to level one should be harder than reaching level eight the first time,
        // or looping would feel like a step backwards.
        assertTrue(Difficulty.NORMAL.bossScale(1, 2) > Difficulty.NORMAL.bossScale(8, 1));
    }

    @Test
    void theChosenDifficultyOrdersTheFlagships() {
        for (int level = 1; level <= 8; level++) {
            assertTrue(Difficulty.EASY.bossScale(level, 1) < Difficulty.NORMAL.bossScale(level, 1));
            assertTrue(Difficulty.NORMAL.bossScale(level, 1) < Difficulty.HARD.bossScale(level, 1));
        }
    }

    @Test
    void everyDifficultyStaysHarderThanNothing() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(difficulty.bossScale(1, 1) > 0.5,
                    difficulty + " would make the flagship a pushover");
        }
    }
}
