package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.prefs.Difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three archetypes have to stay three different fights, and the presets have to stay ordered.
 *
 * Everything here is a property rather than a number, so the hulls can be retuned freely; what must
 * not drift is that a scout is the quick unpredictable one, a cruiser is the slow heavy one, and no
 * preset is accidentally softer than the one below it.
 */
class EnemyVarietyTest {

    private static final PlayerShip TARGET =
            new PlayerShip(1, "P1", Facing.UP, 500, 700);

    @Test
    void theArchetypesDifferInMoreThanHowLongTheyTakeToKill() {
        EnemyShip.EnemyKind scout = EnemyShip.EnemyKind.SCOUT;
        EnemyShip.EnemyKind cruiser = EnemyShip.EnemyKind.CRUISER;

        assertTrue(scout.descentSpeed() > cruiser.descentSpeed(), "the scout is the fast one");
        assertTrue(cruiser.health() > scout.health(), "the cruiser is the strong one");
        assertTrue(scout.weave() > cruiser.weave(), "the scout is the unpredictable one");
        assertTrue(cruiser.fireFactor() < scout.fireFactor(), "the cruiser is the gun platform");
    }

    /**
     * The swerve has to beat the tracking term, or it is a wobble rather than a decision.
     *
     * A ship that only ever closes -- faster, then slower -- can still be led. One that genuinely
     * breaks off and comes back cannot, and that is the whole difference the amplitude buys.
     */
    @Test
    void aScoutSometimesBreaksAwayFromThePlayerRatherThanClosing() {
        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 100, 0);

        boolean closed = false;
        boolean fled = false;
        for (int tick = 0; tick < 200; tick++) {
            scout.trackAcross(TARGET);
            // The player sits to the right, so closing is positive sideways travel.
            closed |= scout.velocityX() > 0;
            fled |= scout.velocityX() < 0;
            scout.update();
        }

        assertTrue(closed, "a scout still has to come at you");
        assertTrue(fled, "and it has to sometimes swerve off, or it is just a fast straight line");
    }

    @Test
    void aCruiserHoldsItsLineSoYouCanDecideToLeave() {
        EnemyShip cruiser = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER, 100, 0);

        for (int tick = 0; tick < 200; tick++) {
            cruiser.trackAcross(TARGET);
            assertFalse(cruiser.velocityX() < 0, "a cruiser should never swerve away");
            cruiser.update();
        }
    }

    /** Two ships of the same archetype entering apart must not trace one line. */
    @Test
    void shipsInAWaveSwerveOnTheirOwnClocks() {
        EnemyShip left = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 100, 0);
        EnemyShip right = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 140, 0);

        boolean everDiffered = false;
        for (int tick = 0; tick < 120; tick++) {
            left.trackAcross(TARGET);
            right.trackAcross(TARGET);
            everDiffered |= Math.abs(left.velocityX() - right.velocityX()) > 0.5;
            left.update();
            right.update();
        }

        assertTrue(everDiffered, "a pair of scouts should not fly in formation by accident");
    }

    @Test
    void aPresetScalesTheHullOfAnOrdinaryEnemy() {
        int authored = EnemyShip.EnemyKind.FIGHTER.health();
        EnemyShip tough = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 100, 100,
                Difficulty.DIE.enemyScale());

        tough.takeDamage(authored);

        assertTrue(tough.isAlive(), "the hardest preset's hull should outlast one authored bar");
    }

    /** Hull may climb without limit; closing speed may not, or a wave cannot be flown through. */
    @Test
    void aPresetNeverMakesAnEnemyFasterThanHalfAgain() {
        EnemyShip quick = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 100, 100,
                Difficulty.DIE.enemyScale());

        double authored = EnemyShip.EnemyKind.SCOUT.descentSpeed();

        assertTrue(quick.velocityY() > authored, "the hardest preset should still be faster");
        assertTrue(quick.velocityY() <= authored * 1.5 + 1e-9, "but capped at half again");
    }

    /**
     * The two new presets have to be harder on every lever, not just on the ones easy to notice.
     *
     * Moving one and forgetting the rest is how a "hardest" mode ends up softer than HARD in the
     * way that actually kills you.
     */
    @Test
    void theTwoPunishingPresetsAreHarderThanHardOnEveryLever() {
        Difficulty[] ladder = {Difficulty.HARD, Difficulty.SUFFER, Difficulty.DIE};
        for (int i = 1; i < ladder.length; i++) {
            Difficulty softer = ladder[i - 1];
            Difficulty harder = ladder[i];
            assertTrue(harder.asteroidChance() > softer.asteroidChance(), harder + " rocks");
            assertTrue(harder.enemyChance() > softer.enemyChance(), harder + " wave rate");
            assertTrue(harder.enemyFireCooldown() < softer.enemyFireCooldown(), harder + " fire rate");
            assertTrue(harder.maxEnemies() > softer.maxEnemies(), harder + " enemy cap");
            assertTrue(harder.enemyScale() > softer.enemyScale(), harder + " hulls");
            assertTrue(harder.heavyBias() > softer.heavyBias(), harder + " wave mix");
            assertTrue(harder.bossScale(1, 1) > softer.bossScale(1, 1), harder + " flagship");
        }
    }

    /**
     * The regression anchor for the whole authored-ship change.
     *
     * An all-defaults {@link WaveShip} has to produce exactly the ship the plain constructor does,
     * to the bit -- box, hull, speed and rate of fire. That equality is what lets the rest of the
     * suite, which builds ships the old way, go on being the regression test for everything else.
     */
    @Test
    void anAuthoredShipWithNoDeviationsIsExactlyItsArchetype() {
        EnemyShip plain = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 120, 90);
        EnemyShip ordered = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 120, 90,
                1, WaveShip.at(EnemyShip.EnemyKind.FIGHTER, 0));

        assertEquals(plain.width(), ordered.width(), 0.0);
        assertEquals(plain.height(), ordered.height(), 0.0);
        assertEquals(plain.velocityY(), ordered.velocityY(), 0.0);
        assertEquals(plain.fireFactor(), ordered.fireFactor(), 0.0);
        assertEquals(plain.shots(), ordered.shots());
        assertEquals(plain.remainingHealthFraction(), ordered.remainingHealthFraction(), 0.0);
    }

    /**
     * A wave may resize a hull, and the drawn box and the hitbox move together because they are the
     * same two numbers -- {@code Renderer.drawSprite} and {@code Entity.intersects} both read them.
     * This is the assertion that keeps them the same two numbers.
     */
    @Test
    void anAuthoredShipIsDrawnAtTheBoxItIsHitAt() {
        for (double size : new double[] {0.8, 1.0, 1.45}) {
            WaveShip ordered = WaveShip.at(EnemyShip.EnemyKind.CRUISER, 0).size(size);
            EnemyShip ship = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER,
                    0, 0, 1, ordered);

            assertEquals(Sprite.L1_CRUISER.width() * size, ship.width(), 1e-9);
            assertEquals(Sprite.L1_CRUISER.height() * size, ship.height(), 1e-9);
        }
    }

    /** Authoring is not a way round the one limit that keeps a wave flyable. */
    @Test
    void anAuthoredShipIsNeverFasterThanThePresetCapAllows() {
        WaveShip racer = WaveShip.at(EnemyShip.EnemyKind.SCOUT, 0).speed(3);
        EnemyShip ship = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 0, 0,
                Difficulty.DIE.enemyScale(), racer);

        assertTrue(ship.velocityY() <= EnemyShip.EnemyKind.SCOUT.descentSpeed() * 1.5 + 1e-9,
                "the cap is on the product, not on either multiplier");
    }

    /** Slowing down is never capped -- only speeding up is, and only because of the player. */
    @Test
    void anAuthoredShipCanAlwaysBeSlowedDown() {
        WaveShip lumbering = WaveShip.at(EnemyShip.EnemyKind.CRUISER, 0).speed(0.5);
        EnemyShip ship = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER, 0, 0,
                Difficulty.DIE.enemyScale(), lumbering);

        assertTrue(ship.velocityY() < EnemyShip.EnemyKind.CRUISER.descentSpeed(),
                "a wave that asked for a slow hull should get one on every preset");
    }

    /**
     * A hit raises the bar, and three quiet seconds lower it.
     *
     * The tick budget is the point: the request was three seconds, and 180 of them is what that is
     * at the fixed sixty-step second. This is also the test that would catch the timer being put in
     * {@code update()}, where the five flagship subclasses override it away and it would never run.
     */
    @Test
    void aHitRaisesTheBarAndThreeQuietSecondsLowersIt() {
        EnemyShip fighter = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 0, 0);

        fighter.takeDamage(5);
        assertEquals(1.0, fighter.hitBarAlpha(), 1e-9, "a hit should raise a full bar");

        for (int tick = 0; tick < 179; tick++) {
            fighter.tickTimers();
        }
        assertTrue(fighter.hitBarAlpha() > 0, "the bar should still be up just under three seconds");

        fighter.tickTimers();
        assertEquals(0, fighter.hitBarAlpha(), 1e-9, "and gone at three");
    }

    /**
     * "Three seconds of not being hit" is a different property from "three seconds from the first
     * hit", and only a second hit inside the window tells them apart.
     */
    @Test
    void aSecondHitRestartsTheWindow() {
        EnemyShip fighter = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 0, 0);

        fighter.takeDamage(5);
        for (int tick = 0; tick < 170; tick++) {
            fighter.tickTimers();
        }
        fighter.takeDamage(5);
        for (int tick = 0; tick < 20; tick++) {
            fighter.tickTimers();
        }

        assertTrue(fighter.hitBarAlpha() > 0, "the second hit should have restarted the three seconds");
    }

    /**
     * A shot a live head absorbed still raises the torso's bar, and still moves none of its hull.
     *
     * The pairing is the whole point: a full bar that will not budge reads as armour, where no bar
     * at all reads as the shot having missed. Same reason {@code MechPart}'s guarded cockpit still
     * flashes and still plays. Worth a test because the obvious tidy-up -- moving the stamp below
     * the shield check, where the damage is -- silently removes it.
     */
    @Test
    void armourRaisesTheBarWithoutLosingHull() {
        EnemyShip hydra = new EnemyShip(Boss.HYDRA, 400, 90);
        assertFalse(hydra.parts().isEmpty(), "the hydra is supposed to have heads");

        hydra.takeDamage(500);

        assertEquals(1.0, hydra.hitBarAlpha(), 1e-9, "the shot landed, so the bar should show");
        assertEquals(1.0, hydra.hullFraction(), 1e-9, "and the heads should have eaten all of it");
    }

    /**
     * A floating bar reads the box it is drawn over, not the fight.
     *
     * {@code remainingHealthFraction} sums the parts, which is right for the HUD's boss bar and
     * wrong here: with its heads dead and its own hull untouched a torso would pop a bar into
     * existence at just over half and drain from there, which reads as a bug rather than as a boss.
     */
    @Test
    void aTorsoBarReadsItsOwnHullNotTheWholeFight() {
        EnemyShip hydra = new EnemyShip(Boss.HYDRA, 400, 90);
        // Shot down rather than kill()ed: kill() flips the flag without touching health, and the
        // fraction this test is about is computed from health rather than from the flag.
        for (EnemyShip head : hydra.parts()) {
            head.takeDamage(Integer.MAX_VALUE / 2);
        }

        assertEquals(1.0, hydra.hullFraction(), 1e-9, "the torso itself is untouched");
        assertTrue(hydra.remainingHealthFraction() < 0.6,
                "while the fight as a whole is well past half");
    }
}
