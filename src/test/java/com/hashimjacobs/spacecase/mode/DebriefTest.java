package com.hashimjacobs.spacecase.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The debrief is pure arithmetic over two snapshots, which is what makes it assertable. */
class DebriefTest {

    private static final int PAR = 10_000;

    private static final Debrief.Tally CLEAN_START =
            new Debrief.Tally(0, 0, 0, 0, 0, 3);

    @Test
    void statsAreTheDifferenceBetweenTheSnapshotsNotTheRunTotals() {
        Debrief.Tally before = new Debrief.Tally(40, 12, 500, 300, 220, 2);
        Debrief.Tally after = new Debrief.Tally(57, 15, 620, 390, 260, 1);

        Debrief debrief = Debrief.of("ACE", 3, PAR, before, after, PAR);

        assertEquals(17, debrief.enemiesKilled(), "only this level's kills count");
        assertEquals(3, debrief.asteroidsDestroyed());
        assertEquals(120, debrief.shotsFired());
        assertEquals(90, debrief.shotsHit());
        assertEquals(40, debrief.damageTaken());
        assertEquals(1, debrief.livesLost());
    }

    @Test
    void accuracyIsHitsOverShotsAsAWholePercentage() {
        assertEquals(75, Debrief.accuracyPercent(120, 90));
        assertEquals(0, Debrief.accuracyPercent(0, 0), "firing nothing is not perfect aim");
        assertEquals(100, Debrief.accuracyPercent(10, 10));
    }

    @Test
    void accuracyCannotExceedOneHundredPercent() {
        // A tri-shot volley counts three projectiles, so hits should never outrun shots -- but the
        // cap is what keeps a miscount from paying an unbounded marksman bonus.
        assertEquals(100, Debrief.accuracyPercent(10, 40));
    }

    @Test
    void theFlagshipBountyRisesWithTheLevel() {
        int early = bountyFor(1);
        int late = bountyFor(8);
        assertTrue(late > early, "level 8's flagship should pay more than level 1's; " + early
                + " then " + late);
    }

    @Test
    void takingNoDamagePaysTheUntouchedBonus() {
        Debrief.Tally unharmed = new Debrief.Tally(10, 0, 100, 50, 0, 3);
        Debrief clean = Debrief.of("ACE", 1, PAR, CLEAN_START, unharmed, PAR);
        assertTrue(hasBonus(clean, "Untouched"));

        Debrief.Tally hurt = new Debrief.Tally(10, 0, 100, 50, 1, 3);
        Debrief scratched = Debrief.of("ACE", 1, PAR, CLEAN_START, hurt, PAR);
        assertFalse(hasBonus(scratched, "Untouched"), "a single point of damage forfeits it");
    }

    @Test
    void losingALifeForfeitsTheUnbrokenBonus() {
        Debrief.Tally survived = new Debrief.Tally(10, 0, 100, 50, 40, 3);
        assertTrue(hasBonus(Debrief.of("ACE", 1, PAR, CLEAN_START, survived, PAR), "Unbroken"));

        Debrief.Tally died = new Debrief.Tally(10, 0, 100, 50, 40, 2);
        assertFalse(hasBonus(Debrief.of("ACE", 1, PAR, CLEAN_START, died, PAR), "Unbroken"));
    }

    @Test
    void clearingUnderParPaysASpeedBonusAndOverParPaysNone() {
        Debrief.Tally end = new Debrief.Tally(10, 0, 100, 50, 40, 3);

        Debrief quick = Debrief.of("ACE", 1, PAR, CLEAN_START, end, PAR - 1_000);
        assertTrue(hasBonus(quick, "Swift"));

        Debrief slow = Debrief.of("ACE", 1, PAR, CLEAN_START, end, PAR + 5_000);
        assertFalse(hasBonus(slow, "Swift"), "a bonus for being slow would be no bonus at all");
    }

    @Test
    void theTotalIsTheSumOfTheListedBonuses() {
        Debrief.Tally end = new Debrief.Tally(10, 0, 100, 50, 0, 3);
        Debrief debrief = Debrief.of("ACE", 4, PAR, CLEAN_START, end, PAR / 2);

        int listed = debrief.bonuses().stream().mapToInt(Debrief.Bonus::points).sum();
        assertEquals(listed, debrief.totalBonus(), "the printed lines must add up to what is paid");
        assertTrue(debrief.totalBonus() > 0);
    }

    @Test
    void clearTimeIsReportedInSeconds() {
        Debrief.Tally end = new Debrief.Tally(0, 0, 0, 0, 0, 3);
        Debrief debrief = Debrief.of("ACE", 1, PAR, CLEAN_START, end, 600);
        assertEquals(10, debrief.clearSeconds(), "600 steps at 60 a second is ten seconds");
    }

    private static int bountyFor(int levelNumber) {
        Debrief.Tally end = new Debrief.Tally(0, 0, 0, 0, 99, 3);
        Debrief debrief = Debrief.of("ACE", levelNumber, PAR, CLEAN_START, end, PAR);
        int bounty = debrief.bonuses().stream()
                .filter(bonus -> bonus.label().equals("Flagship bounty"))
                .mapToInt(Debrief.Bonus::points)
                .findFirst()
                .orElseThrow();
        return bounty;
    }

    private static boolean hasBonus(Debrief debrief, String prefix) {
        boolean present = debrief.bonuses().stream()
                .anyMatch(bonus -> bonus.label().startsWith(prefix));
        return present;
    }
}
