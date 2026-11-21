package com.hashimjacobs.spacecase.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What helping pays: a share of the level's own pay, a floor under it, and a flat point count. */
class HelperBonusTest {

    private static final int PAR = 10_000;
    private static final Debrief.Tally START = new Debrief.Tally(0, 0, 0, 0, 0, 3);

    private static Debrief flown(int kills, int level) {
        return Debrief.of("ACE", level, PAR, START,
                new Debrief.Tally(kills, kills / 4, kills * 2, kills * 2, 0, 3), PAR);
    }

    @Test
    void aHelperIsPaidAShareOfTheLevelOnTopOfIt() {
        Debrief debrief = flown(60, 5);

        HelperBonus bonus = HelperBonus.of(debrief);

        assertEquals(Math.max(25, debrief.credits() / 2), bonus.credits());
        assertEquals(debrief.totalBonus() / 4, bonus.career());
        assertTrue(bonus.career() > 0, "a level flown well moves the helper toward their next rank");
    }

    @Test
    void helpingIsNeverWorthLessThanTheFloor() {
        // Nothing shot, nothing earned: the level still pays its helper for turning up.
        Debrief idle = Debrief.of("ACE", 1, PAR, START, new Debrief.Tally(0, 0, 0, 0, 500, 1), PAR * 3);

        HelperBonus bonus = HelperBonus.of(idle);

        assertTrue(bonus.credits() >= 25, "paid " + bonus.credits());
        assertTrue(bonus.career() >= 0);
    }

    @Test
    void flyingBetterPaysMore() {
        assertTrue(HelperBonus.of(flown(120, 8)).credits() > HelperBonus.of(flown(4, 1)).credits());
        assertTrue(HelperBonus.of(flown(120, 8)).career() >= HelperBonus.of(flown(4, 1)).career());
    }

    @Test
    void theBonusNeverOutweighsTheLevelItIsFor() {
        Debrief debrief = flown(80, 6);
        HelperBonus bonus = HelperBonus.of(debrief);

        assertTrue(bonus.career() <= debrief.totalBonus());
        assertTrue(bonus.credits() <= Math.max(25, debrief.credits()));
    }

    /** The relay adds the same number to the helpers' table, so a pilot's total and their row agree. */
    @Test
    void everyLevelIsWorthTheSameHelpPoints() {
        assertEquals(100, HelperBonus.of(flown(4, 1)).helpPoints());
        assertEquals(100, HelperBonus.of(flown(120, 9)).helpPoints());
        assertEquals(100, HelperBonus.HELP_POINTS);
    }
}
