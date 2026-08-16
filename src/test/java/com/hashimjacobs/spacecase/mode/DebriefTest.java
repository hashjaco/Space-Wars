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

    /** Kills through the ladder, with the tally holding everything else constant. */
    private static Debrief afterKilling(int enemies) {
        Debrief.Tally end = new Debrief.Tally(enemies, 0, 0, 0, 99, 3);
        return Debrief.of("ACE", 1, PAR, CLEAN_START, end, PAR);
    }

    /** Damage through the ladder, likewise. */
    private static Debrief afterTaking(int damage) {
        Debrief.Tally end = new Debrief.Tally(0, 0, 0, 0, damage, 3);
        return Debrief.of("ACE", 1, PAR, CLEAN_START, end, PAR);
    }

    private static int bonusStartingWith(Debrief debrief, String prefix) {
        return debrief.bonuses().stream()
                .filter(bonus -> bonus.label().startsWith(prefix))
                .mapToInt(Debrief.Bonus::points)
                .findFirst()
                .orElse(0);
    }

    @Test
    void theKillLadderPaysMoreAtEachTier() {
        assertEquals(0, bonusStartingWith(afterKilling(9), "Purge"), "below the first rung");
        int sweep = bonusStartingWith(afterKilling(10), "Purge");
        int strike = bonusStartingWith(afterKilling(20), "Purge");
        int purge = bonusStartingWith(afterKilling(35), "Purge");

        assertTrue(sweep > 0);
        assertTrue(strike > sweep);
        assertTrue(purge > strike);
        assertEquals(purge, bonusStartingWith(afterKilling(200), "Purge"), "the top rung is the cap");
    }

    @Test
    void onlyOneKillTierIsEverAwarded() {
        long rows = afterKilling(40).bonuses().stream()
                .filter(bonus -> bonus.label().startsWith("Purge"))
                .count();
        assertEquals(1, rows);
    }

    @Test
    void theKillBonusNamesTheCountSoANearMissIsLegible() {
        assertTrue(afterKilling(22).bonuses().stream()
                .anyMatch(bonus -> bonus.label().equals("Purge  22 kills")));
    }

    @Test
    void flyingCleanPaysBestAndEachTierBelowItPaysLess() {
        int untouched = bonusStartingWith(afterTaking(0), "Untouched");
        int unscathed = bonusStartingWith(afterTaking(20), "Unscathed");
        int grazed = bonusStartingWith(afterTaking(60), "Grazed");

        assertTrue(untouched > unscathed);
        assertTrue(unscathed > grazed);
        assertTrue(grazed > 0);
        assertEquals(0, bonusStartingWith(afterTaking(61), "Grazed"), "past the last rung");
    }

    @Test
    void exactlyOneDamageTierIsEverAwarded() {
        for (int damage : new int[]{0, 1, 20, 21, 60, 61, 500}) {
            long rows = afterTaking(damage).bonuses().stream()
                    .filter(bonus -> bonus.label().startsWith("Untouched")
                            || bonus.label().startsWith("Unscathed")
                            || bonus.label().startsWith("Grazed"))
                    .count();
            assertTrue(rows <= 1, "two damage rows at " + damage + " damage");
        }
    }

    /** The whole point of the ladder: avoiding damage must never pay less than taking it. */
    @Test
    void takingLessDamageNeverPaysLess() {
        int previous = Integer.MAX_VALUE;
        for (int damage : new int[]{0, 20, 60, 61}) {
            int paid = afterTaking(damage).totalBonus();
            assertTrue(paid <= previous, "taking " + damage + " damage paid more than taking less");
            previous = paid;
        }
    }

    @Test
    void garageCreditsTrackTheBonusAndTheLevel() {
        Debrief.Tally end = new Debrief.Tally(0, 0, 0, 0, 99, 3);
        Debrief early = Debrief.of("ACE", 1, PAR, CLEAN_START, end, PAR);
        Debrief late = Debrief.of("ACE", 8, PAR, CLEAN_START, end, PAR);

        assertTrue(early.credits() > 0, "even a scrappy clear should buy something");
        assertTrue(late.credits() > early.credits(),
                "a later level pays more, matching its bigger bounty");
        assertTrue(late.credits() < late.totalBonus(),
                "credits are a fraction of the score bonus, not a second copy of it");
    }

    /**
     * The credit rate against what the garage actually charges.
     *
     * Loose bounds on purpose -- this is a guard against the divisor being moved without anyone
     * checking what it does to progression, not a pin on the exact numbers. The first upgrade step
     * costs 40 and the whole catalogue is roughly 2500.
     */
    @Test
    void aLevelPaysEnoughToBuySomethingButNotTheWholeGarage() {
        Debrief.Tally decent = new Debrief.Tally(18, 4, 200, 130, 45, 3);
        Debrief early = Debrief.of("ACE", 1, PAR, CLEAN_START, decent, PAR);
        Debrief late = Debrief.of("ACE", 8, PAR, CLEAN_START, decent, PAR);

        assertTrue(early.credits() >= 40,
                "a first-level clear should buy at least one upgrade step, got " + early.credits());
        assertTrue(late.credits() < 500,
                "one level should not bankroll a whole track, got " + late.credits());
        assertTrue(late.credits() > early.credits(), "later levels pay more");
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
