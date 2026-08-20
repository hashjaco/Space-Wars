package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camera kick, which decays on the tick rather than on the frame.
 *
 * Pure arithmetic on World, so none of this needs a canvas.
 */
class ScreenShakeTest {

    private static void run(World world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tickScenery();
        }
    }

    @Test
    void aFreshWorldIsAlreadySettled() {
        assertEquals(0, new World(GameMode.SOLO).shakeRemaining(), 1e-9);
    }

    @Test
    void aKickDecaysToNothingAndStaysThere() {
        World world = new World(GameMode.SOLO);
        world.shake(10);

        assertEquals(10, world.shakeRemaining(), 1e-9);
        run(world, 9);
        assertTrue(world.shakeRemaining() < 10 && world.shakeRemaining() > 0,
                "should be part way through, was " + world.shakeRemaining());
        run(world, 30);
        assertEquals(0, world.shakeRemaining(), 1e-9);
    }

    /** A stray asteroid popping must not cut short the kick from a flagship going up. */
    @Test
    void aWeakerKickDoesNotInterruptAStrongerOneStillRunning() {
        World world = new World(GameMode.SOLO);
        world.shake(12);
        run(world, 3);
        double before = world.shakeRemaining();

        world.shake(1);

        assertEquals(before, world.shakeRemaining(), 1e-9);
    }

    @Test
    void aStrongerKickTakesOver() {
        World world = new World(GameMode.SOLO);
        world.shake(2);
        world.shake(9);

        assertEquals(9, world.shakeRemaining(), 1e-9);
    }

    /** Bigger wreck, bigger kick -- which is what stands in for a per-caller magnitude. */
    @Test
    void aFlagshipThrowsTheCameraHarderThanAScoutDoes() {
        World scoutDies = new World(GameMode.SOLO);
        scoutDies.addExplosion(
                new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 0, 0), Explosion.LARGE);

        World rockPops = new World(GameMode.SOLO);
        rockPops.addExplosion(new Asteroid(Sprite.ASTEROID_SMALL, 0, 0, 20, 9, 10), Explosion.SMALL);

        assertTrue(scoutDies.shakeRemaining() > rockPops.shakeRemaining(),
                "a ship should outkick a pebble: " + scoutDies.shakeRemaining()
                        + " vs " + rockPops.shakeRemaining());
    }
}
