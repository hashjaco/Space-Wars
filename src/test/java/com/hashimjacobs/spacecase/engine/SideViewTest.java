package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A level running right-to-left, exercised through the same World the game uses.
 *
 * OrientationTest pins the arithmetic; this pins that the arithmetic actually reached the engine.
 */
class SideViewTest {

    /** What the renderer reads to pick a profile's loop: a shot and a hit each hold theirs, briefly. */
    @Test
    void aShotAndAHitEachHoldTheirPoseAndThePitchStaysSmall() {
        EnemyShip ship = new EnemyShip(EnemyShip.EnemyKind.FIGHTER,
                com.hashimjacobs.spacecase.asset.Sprite.L2_FIGHTER, 500, 400);
        assertEquals(0, ship.firedTicks());
        ship.markFired();
        ship.takeDamage(1);
        assertTrue(ship.firedTicks() > 0 && ship.flinchTicks() > 0);
        ship.setVelocity(-2, -9);
        for (int tick = 0; tick < 200; tick++) {
            ship.tickTimers();
            assertTrue(Math.abs(ship.pitch()) <= 14.0001, "pitched " + ship.pitch());
        }
        assertEquals(0, ship.firedTicks());
        assertEquals(0, ship.flinchTicks());
        assertTrue(ship.pitch() > 10, "a hull climbing hard should have its nose up");
    }


    private static World sideView(GameMode mode) {
        World world = new World(mode);
        world.setOrientation(Orientation.RIGHT_TO_LEFT);
        return world;
    }

    @Test
    void theSoloPilotHoldsTheLeftWallFacingRight() {
        World world = sideView(GameMode.SOLO);
        PlayerShip solo = world.players().get(0);

        assertEquals(Facing.RIGHT, solo.facing());
        assertTrue(solo.x() < GameConfig.WIDTH / 4,
                "should be near the left wall, was at x=" + solo.x());
    }

    /** Side by side becomes stacked, which is the same sentence in arena terms. */
    @Test
    void coopPilotsStackVerticallyInsteadOfSittingSideBySide() {
        World world = sideView(GameMode.COOP);
        PlayerShip first = world.players().get(0);
        PlayerShip second = world.players().get(1);

        assertEquals(first.x(), second.x(), 1e-9, "both should hold the same column");
        assertTrue(Math.abs(first.y() - second.y()) > 100, "they should be spread down the lane");
        assertEquals(Facing.RIGHT, second.facing());
    }

    @Test
    void anArrivingEnemyFliesTowardTheLeftWall() {
        World world = sideView(GameMode.SOLO);
        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT,
                GameConfig.WIDTH, 400);

        world.addEnemy(scout);

        assertTrue(scout.velocityX() < 0, "should advance leftward");
        assertEquals(0, scout.velocityY(), 1e-9, "and not drift vertically on arrival");
    }

    @Test
    void aPickupDriftsLeftRatherThanDown() {
        World world = sideView(GameMode.SOLO);
        PowerUp pickup = new PowerUp(PowerUp.Kind.SHIELD, GameConfig.WIDTH, 300);

        world.addPowerUp(pickup);

        assertTrue(pickup.velocityX() < 0);
        assertEquals(0, pickup.velocityY(), 1e-9);
    }

    /** The station-keeping clamp, ninety degrees round: the boss stops short of the right wall. */
    @Test
    void aBossHoldsStationOffTheRightWall() {
        World world = sideView(GameMode.SOLO);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, Orientation.RIGHT_TO_LEFT.arenaWidth(), 400);
        world.addEnemy(boss);

        for (int tick = 0; tick < 2000; tick++) {
            world.update();
            world.sweep();
        }

        assertTrue(boss.isAlive(), "the boss must not be culled on the way in");
        double depth = Orientation.RIGHT_TO_LEFT.depth(boss.x(), boss.y(),
                boss.width(), boss.height());
        // Undriven, it sinks to the deep end of its patrol band and stops there (EnemyShip.patrol).
        assertTrue(depth >= 88 && depth <= 172,
                "should hold 90-170 in from the right wall, held at " + depth);
    }

    @Test
    void anEnemyThatReachesTheLeftWallIsCulledAndOneStillInsideIsNot() {
        World world = sideView(GameMode.SOLO);
        EnemyShip escaped = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, -200, 300);
        EnemyShip fighting = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 500, 300);
        world.addEnemy(escaped);
        world.addEnemy(fighting);

        world.update();
        world.sweep();

        assertFalse(world.enemies().contains(escaped), "past the far wall, should be gone");
        assertTrue(world.enemies().contains(fighting));
    }

    /** Something still off the entry edge must survive the trip in, or bosses die on arrival. */
    @Test
    void somethingStillOffTheRightEdgeIsNotCulled() {
        World world = sideView(GameMode.SOLO);
        EnemyShip arriving = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER,
                GameConfig.WIDTH + 60, 300);
        world.addEnemy(arriving);

        world.update();
        world.sweep();

        assertTrue(world.enemies().contains(arriving));
    }

    @Test
    void switchingBackToTopDownRestoresTheVerticalLayout() {
        World world = sideView(GameMode.SOLO);

        world.setOrientation(Orientation.TOP_DOWN);
        PlayerShip solo = world.players().get(0);

        assertEquals(Facing.UP, solo.facing());
        assertTrue(solo.y() > GameConfig.HEIGHT * 0.7, "back to the bottom of the arena");
    }
}
