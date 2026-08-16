package com.hashimjacobs.spacecase.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the ConcurrentModificationException the old engine threw.
 *
 * asteroidCollisions and enemyCollisions each called removeAll on the very bullet list they were
 * iterating with forEach, so the first bullet-versus-hazard collision threw on the JavaFX thread
 * and kept throwing every frame after that.
 */
class WorldSweepTest {

    @Test
    void killingEveryBulletWhileIteratingDoesNotThrow() {
        World world = new World(GameMode.SOLO);
        for (int i = 0; i < 50; i++) {
            world.addBullet(bullet(i * 3, 400));
        }

        // The shape that used to blow up: walk the live list and mark everything dead as you go.
        assertDoesNotThrow(() -> {
            for (Bullet bullet : world.bullets()) {
                bullet.kill();
            }
            world.sweep();
        });
        assertEquals(0, world.bullets().size(), "sweep should clear every dead bullet");
    }

    @Test
    void sweepRemovesOnlyDeadEntities() {
        World world = new World(GameMode.SOLO);
        Bullet doomed = bullet(10, 10);
        Bullet survivor = bullet(500, 500);
        world.addBullet(doomed);
        world.addBullet(survivor);

        doomed.kill();
        world.sweep();

        assertEquals(List.of(survivor), world.bullets());
    }

    @Test
    void updateDoesNotRemoveAnything() {
        World world = new World(GameMode.SOLO);
        Bullet leaving = bullet(100, 5);
        leaving.setVelocity(0, -400);
        world.addBullet(leaving);

        world.update();

        assertTrue(world.bullets().contains(leaving),
                "update marks out-of-bounds entities but must leave removal to sweep");
        assertTrue(!leaving.isAlive(), "leaving the arena should mark the bullet dead");

        world.sweep();
        assertEquals(0, world.bullets().size());
    }

    /**
     * The actual reported failure, driven through the real collision system: a frame in which
     * every bullet strikes an asteroid. This is the case that threw on the old engine.
     */
    @Test
    void resolvingAFrameWhereEveryBulletHitsDoesNotThrow() {
        World world = new World(GameMode.SOLO);
        PlayerShip shooter = world.players().get(0);
        // Overlap each bullet with an asteroid so all 40 pairs collide in the same frame.
        for (int i = 0; i < 40; i++) {
            world.addBullet(ownedBullet(300, 300, shooter));
            world.addAsteroid(asteroid(300, 300));
        }

        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT);

        assertDoesNotThrow(() -> {
            collisions.resolve(world);
            world.sweep();
        });
        assertEquals(0, world.bullets().size(), "every bullet should have been consumed");
        assertTrue(shooter.score() > 0, "destroying asteroids should score");
    }

    @Test
    void repeatedFramesOfHeavyCollisionStaySafe() {
        World world = new World(GameMode.SOLO);
        PlayerShip shooter = world.players().get(0);
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT);

        assertDoesNotThrow(() -> {
            for (int frame = 0; frame < 200; frame++) {
                for (int i = 0; i < 6; i++) {
                    world.addBullet(ownedBullet(200 + i, 300, shooter));
                    world.addAsteroid(asteroid(200 + i, 300));
                }
                world.update();
                collisions.resolve(world);
                world.sweep();
            }
        });
    }

    /**
     * A bullet that drifts off the side is culled like one that flies off the top.
     *
     * Tri-shot spread and every boss fan already produce these, so they were accumulating for the
     * length of a run before this was fixed.
     */
    @Test
    void bulletsThatLeaveSidewaysAreCulled() {
        World world = new World(GameMode.SOLO);
        Bullet offLeft = new Bullet(Sprite.PLAYER_BULLET, -40, 400, -4, 0, null, 10);
        Bullet offRight = new Bullet(Sprite.PLAYER_BULLET,
                com.hashimjacobs.spacecase.GameConfig.WIDTH + 10, 400, 4, 0, null, 10);
        Bullet inside = new Bullet(Sprite.PLAYER_BULLET, 400, 400, 4, 0, null, 10);
        world.addBullet(offLeft);
        world.addBullet(offRight);
        world.addBullet(inside);

        world.update();
        world.sweep();

        assertEquals(1, world.bullets().size(), "only the bullet still in the arena should remain");
        assertTrue(world.bullets().contains(inside));
    }

    private static Bullet bullet(double x, double y) {
        Bullet created = new Bullet(Sprite.PLAYER_BULLET, x, y, 0, -1, null, 10);
        return created;
    }

    private static Bullet ownedBullet(double x, double y, PlayerShip owner) {
        Bullet created = new Bullet(Sprite.PLAYER_BULLET, x, y, 0, -1, owner, 30);
        return created;
    }

    private static Asteroid asteroid(double x, double y) {
        Asteroid created = new Asteroid(Sprite.ASTEROID_SMALL, x, y, 20, 9, 10);
        return created;
    }
}
