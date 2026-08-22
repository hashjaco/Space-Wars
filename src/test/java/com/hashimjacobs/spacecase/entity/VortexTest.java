package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vortex is the only pattern in the game whose spread is not a constant.
 *
 * That distinction is the whole reason this file exists. {@code EnemyWeapons.firePattern} reads the
 * spread twice per volley -- once to place the first shot and once to step between them -- and for
 * six patterns over four galaxies it was safe to read it twice because it could not change in
 * between. Now it can, so two things need holding: that nothing else moved, and that the one thing
 * which does move stays inside a range that is still a ring.
 */
class VortexTest {

    /** Ticks sampled across several full breathe cycles, including the awkward ones. */
    private static final int[] TICKS = {0, 1, 7, 55, 110, 165, 220, 221, 439, 1000, 12345, 99999};

    /**
     * Every pattern that existed before the vortex is untouched at every tick.
     *
     * This is the assertion that says "no fight in the first four galaxies changed". Without it the
     * per-tick spread is a silent rewrite of every boss in the game.
     */
    @Test
    void everyOtherPhaseIgnoresTheClock() {
        for (BossPhase phase : BossPhase.values()) {
            if (phase == BossPhase.VORTEX) {
                continue;
            }
            for (int tick : TICKS) {
                assertEquals(phase.spreadRadians(), phase.spreadRadiansAt(tick), 0.0,
                        phase + " changed with the clock at tick " + tick);
            }
        }
    }

    /** The vortex does vary, or it is just a RING with more shots. */
    @Test
    void theVortexTightensAndLoosens() {
        double widest = 0;
        double tightest = Double.MAX_VALUE;
        for (int tick = 0; tick < 660; tick++) {
            double spread = BossPhase.VORTEX.spreadRadiansAt(tick);
            widest = Math.max(widest, spread);
            tightest = Math.min(tightest, spread);
        }

        assertTrue(widest - tightest > 0.1,
                "the vortex should visibly breathe, but spanned only " + (widest - tightest));
    }

    /**
     * It never spreads wider than its declared widest, and never crosses itself.
     *
     * The declared value is the ceiling rather than a sample of the curve, which is what lets
     * {@code shots * spreadRadians()} below be a safe bound on the pattern at any moment.
     */
    @Test
    void theVortexStaysInsideItsDeclaredSpread() {
        for (int tick = 0; tick < 3000; tick++) {
            double spread = BossPhase.VORTEX.spreadRadiansAt(tick);

            assertTrue(spread > 0, "a spread of " + spread + " would fire every shot on one heading");
            assertTrue(spread <= BossPhase.VORTEX.spreadRadians() + 1e-9,
                    "wider than declared at tick " + tick + ": " + spread);
        }
    }

    /**
     * At its widest the ring closes but does not overlap itself.
     *
     * A pattern spanning more than a full circle wraps shots back over their own neighbours, which
     * looks like a bug and wastes the volley. Twelve shots span eleven gaps, so the bound is on
     * {@code shots - 1}.
     */
    @Test
    void theWidestRingIsAtMostAFullCircle() {
        BossPhase vortex = BossPhase.VORTEX;
        double span = (vortex.shots() - 1) * vortex.spreadRadians();

        assertTrue(span <= 2 * Math.PI + 1e-9, "the ring overlaps itself: " + span);
        assertTrue(span > 2 * Math.PI * 0.85, "not really a full ring: " + span);
    }

    /** A ring is the point, so it needs enough shots to read as one. */
    @Test
    void theVortexFiresAFullRingsWorthOfShots() {
        assertTrue(BossPhase.VORTEX.shots() > BossPhase.RING.shots(),
                "the vortex covers every direction where RING covers half");
    }
}
