package com.hashimjacobs.spacecase.prefs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankTest {

    @Test
    void theLadderClimbsWithoutAPlateau() {
        Rank[] ladder = Rank.values();
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i].careerScoreRequired() > ladder[i - 1].careerScoreRequired(),
                    ladder[i] + " must cost more than " + ladder[i - 1]);
        }
    }

    @Test
    void aFreshPilotStartsAtTheBottom() {
        assertSame(Rank.RECRUIT, Rank.forCareerScore(0));
        assertSame(Rank.RECRUIT, Rank.forCareerScore(1));
    }

    @Test
    void everyRankIsReachedExactlyAtItsThreshold() {
        for (Rank rank : Rank.values()) {
            assertSame(rank, Rank.forCareerScore(rank.careerScoreRequired()),
                    rank + " should be held the moment its threshold is met");
        }
    }

    @Test
    void onePointShortOfAThresholdIsStillTheRankBelow() {
        for (Rank rank : Rank.values()) {
            if (rank == Rank.RECRUIT) {
                continue;
            }
            Rank held = Rank.forCareerScore(rank.careerScoreRequired() - 1);
            assertNotEquals(rank, held, "a threshold must not be met early");
            assertTrue(held.careerScoreRequired() < rank.careerScoreRequired());
        }
    }

    @Test
    void anEnormousCareerTopsOutRatherThanRunningOffTheEnd() {
        Rank top = Rank.forCareerScore(Integer.MAX_VALUE);
        assertTrue(top.isHighest(), "the ladder must have a top and " + top + " is not it");
        assertSame(top, top.next(), "the top rank's next is itself");
    }

    @Test
    void onlyTheLastRankReportsItselfHighest() {
        Rank[] ladder = Rank.values();
        for (int i = 0; i < ladder.length - 1; i++) {
            assertFalse(ladder[i].isHighest(), ladder[i] + " is not the top of the ladder");
        }
        assertTrue(ladder[ladder.length - 1].isHighest());
    }

    @Test
    void progressRunsFromZeroAtOneThresholdToOneAtTheNext() {
        Rank rank = Rank.CORPORAL;
        Rank above = rank.next();

        assertEquals(0, rank.progressToward(above, rank.careerScoreRequired()), 1e-9);
        assertEquals(1, rank.progressToward(above, above.careerScoreRequired()), 1e-9);

        int midway = (rank.careerScoreRequired() + above.careerScoreRequired()) / 2;
        double half = rank.progressToward(above, midway);
        assertTrue(half > 0.4 && half < 0.6, "halfway between thresholds should read as half; " + half);
    }

    @Test
    void progressIsClampedAndFullAtTheTop() {
        Rank top = Rank.FLEET_MARSHAL;
        assertEquals(1, top.progressToward(top.next(), 0), 1e-9,
                "there is nothing above the top rank to progress toward");
        assertEquals(0, Rank.SERGEANT.progressToward(Rank.SERGEANT.next(), 0), 1e-9,
                "a score below the rank's own threshold must not read as negative progress");
    }

    @Test
    void insigniaMarksRestartWithinEachTier() {
        assertEquals(1, Rank.RECRUIT.insigniaCount());
        assertEquals(Rank.Insignia.CHEVRONS, Rank.RECRUIT.insignia());

        assertEquals(1, Rank.WARRANT_OFFICER.insigniaCount(), "a new tier starts its count again");
        assertEquals(Rank.Insignia.RODS, Rank.WARRANT_OFFICER.insignia());

        assertEquals(1, Rank.ENSIGN.insigniaCount());
        assertEquals(Rank.Insignia.BARS, Rank.ENSIGN.insignia());

        assertEquals(1, Rank.BRIGADIER_GENERAL.insigniaCount());
        assertEquals(Rank.Insignia.STARS, Rank.BRIGADIER_GENERAL.insignia());
        assertEquals(4, Rank.GENERAL.insigniaCount(), "fourth of the general ranks, so four stars");
    }

    @Test
    void everyRankHasALabelAndAShortForm() {
        for (Rank rank : Rank.values()) {
            assertFalse(rank.label().isBlank(), rank + " needs a title");
            assertFalse(rank.abbreviation().isBlank(), rank + " needs a short form");
            assertTrue(rank.insigniaCount() >= 1, rank + " needs at least one insignia mark");
        }
    }
}
