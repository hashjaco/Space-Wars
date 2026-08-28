package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aeon's orbit, and the one thing about this boss that is logic rather than numbers.
 *
 * A {@code BossArt}'s declared size is the collision box, so there is no hitbox to fall back on
 * behind the picture: where the art says the boss is, the boss is. That makes the orbit radii and
 * the declared 300x300 one number in two files, and nothing in the arithmetic notices if they stop
 * agreeing. The failure is silent and it is the worst kind -- the campaign's last boss walking
 * through the line the player spawns on, or an eye swinging off the side of the arena, with a
 * perfectly green suite.
 *
 * The eyes are the real constraint, not the body, and they are the reason this file exists. Their
 * reach is assembled from four constants that all live in {@link BossHead} and none of which
 * {@link VoidEntity} can see. Asserting the outcome rather than the arithmetic is the only way that
 * coupling gets caught: change a neck length in BossHead and this test fails, which is exactly what
 * should happen.
 */
class AeonTest {

    /** Two full laps, so nothing passes by being sampled at a lucky phase of one. */
    private static final int TICKS = 1200;

    /** Where the player's ship starts, from {@code World}: one arena depth less 130. */
    private static final double PLAYER_LINE = GameConfig.HEIGHT - 130;

    private static VoidEntity aeon() {
        return new VoidEntity(Boss.AEON, 400, 90, 1);
    }

    /** Steps the body and every eye together, the way the world does: all are in its enemy list. */
    private static void step(VoidEntity boss) {
        boss.update();
        for (EnemyShip eye : boss.parts()) {
            eye.update();
        }
    }

    @Test
    void aeonFieldsFourEyes() {
        VoidEntity boss = aeon();

        assertEquals(4, boss.parts().size(), "four eyes, through the ordinary parts path");
        for (EnemyShip eye : boss.parts()) {
            assertTrue(eye.isBossPart());
        }
    }

    /**
     * The body stays in the arena for the whole orbit, measured at its edges.
     *
     * At its edges and not at {@code x()}, because a centre-or-origin assertion is vacuously true of
     * anything on screen -- which is the mistake PilotedMechTest shipped with and had corrected.
     */
    @Test
    void theBodyNeverLeavesTheArena() {
        VoidEntity boss = aeon();

        for (int tick = 0; tick < TICKS; tick++) {
            step(boss);
            assertTrue(boss.x() >= 0, "left edge off the arena at tick " + tick);
            assertTrue(boss.x() + boss.width() <= GameConfig.WIDTH,
                    "right edge off the arena at tick " + tick);
            assertTrue(boss.y() >= 0, "leading edge off the arena at tick " + tick);
        }
    }

    /**
     * Neither the body nor any eye reaches the line the player spawns on.
     *
     * This is the fairness rule, and it is the same one the Storm Serpent's reach was retuned for:
     * what decides whether a boss is fair is where its <em>leading edge</em> ends up, which is the
     * orbit plus the art's extent along the arena, plus -- here -- a neck at full stretch and the
     * eye's own box on the end of it.
     */
    @Test
    void nothingReachesThePlayersSpawnLine() {
        VoidEntity boss = aeon();

        for (int tick = 0; tick < TICKS; tick++) {
            step(boss);
            assertTrue(boss.y() + boss.height() < PLAYER_LINE,
                    "the body crossed the player's spawn line at tick " + tick);
            for (EnemyShip eye : boss.parts()) {
                assertTrue(eye.y() + eye.height() < PLAYER_LINE,
                        "an eye crossed the player's spawn line at tick " + tick);
            }
        }
    }

    /** Every eye stays in the lane too. The body fits at a wider orbit than the eyes allow. */
    @Test
    void noEyeSwingsOutOfTheLane() {
        VoidEntity boss = aeon();

        for (int tick = 0; tick < TICKS; tick++) {
            step(boss);
            for (EnemyShip eye : boss.parts()) {
                assertTrue(eye.x() >= 0, "an eye swung off the left at tick " + tick);
                assertTrue(eye.x() + eye.width() <= GameConfig.WIDTH,
                        "an eye swung off the right at tick " + tick);
            }
        }
    }

    /**
     * The orbit is a pure function of age, which is the discipline the class exists to keep.
     *
     * Two of them stepped the same number of times are in the same place, so the position carries no
     * state that can drift, and a paused or resumed fight cannot desynchronise it.
     */
    @Test
    void theOrbitIsAPureFunctionOfAge() {
        VoidEntity first = aeon();
        VoidEntity second = aeon();

        for (int tick = 0; tick < 300; tick++) {
            first.update();
        }
        for (int tick = 0; tick < 300; tick++) {
            second.update();
        }

        assertEquals(first.x(), second.x(), 1e-9);
        assertEquals(first.y(), second.y(), 1e-9);
    }

    /** It really does move, so the assertions above are not passing on a boss that sits still. */
    @Test
    void theOrbitActuallyOrbits() {
        VoidEntity boss = aeon();
        step(boss);
        double startX = boss.x();
        double startY = boss.y();
        double farthestX = 0;
        double farthestY = 0;

        for (int tick = 0; tick < TICKS; tick++) {
            step(boss);
            farthestX = Math.max(farthestX, Math.abs(boss.x() - startX));
            farthestY = Math.max(farthestY, Math.abs(boss.y() - startY));
        }

        assertTrue(farthestX > 300, "the orbit should sweep the lane, moved only " + farthestX);
        assertTrue(farthestY > 50, "the orbit should lean along the arena, moved only " + farthestY);
    }
}
