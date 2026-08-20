package com.hashimjacobs.spacecase.prefs;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.Galaxy;

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
        // Coming back round to level one should be harder than the deepest level of a galaxy, or
        // looping would feel like a step backwards. Ten, not eight: the level ramp now runs to the
        // end of a galaxy, so the tenth is the toughest in-galaxy scale a loop has to beat.
        int lastInGalaxy = Galaxy.LEVELS_PER_GALAXY;
        assertTrue(Difficulty.NORMAL.bossScale(1, 2)
                > Difficulty.NORMAL.bossScale(lastInGalaxy, 1));
    }

    /**
     * The level ramp counts within a galaxy, so each galaxy's opener scales like the very first.
     *
     * Without this the ramp would compound with the bosses' authored health across fifty levels and
     * put the final flagship near three times its written numbers.
     */
    @Test
    void theLevelRampStartsAgainInEveryGalaxy() {
        for (Galaxy galaxy : Galaxy.values()) {
            int opener = galaxy.first().number();
            assertEquals(Difficulty.NORMAL.bossScale(1, 1),
                    Difficulty.NORMAL.bossScale(opener, 1), 1e-9,
                    galaxy + " should open at the same scale as the campaign does");
        }
    }

    @Test
    void flagshipsGetTougherAcrossAWholeGalaxy() {
        double opener = Difficulty.NORMAL.bossScale(1, 1);
        double finale = Difficulty.NORMAL.bossScale(Galaxy.LEVELS_PER_GALAXY, 1);
        assertTrue(finale > opener, "a galaxy has to get harder from one end to the other");
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
