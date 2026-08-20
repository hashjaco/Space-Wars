package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bullet-versus-hazard resolution, which is a flat scan rather than a spatial index.
 *
 * The scan order is a deliberate choice and not an accident of iteration, so it is pinned here:
 * the quadtree this replaced returned candidates in whatever order its subdivision happened to
 * produce, which made an overlapping ship and rock a coin toss.
 */
class CollisionSystemTest {

    private static CollisionSystem system() {
        return new CollisionSystem(SoundPlayer.SILENT, new Random(1));
    }

    private static Bullet playerShotAt(World world, double x, double y) {
        PlayerShip shooter = world.players().get(0);
        return new Bullet(Sprite.PLAYER_BULLET, x, y, 0, -10, shooter, 10);
    }

    @Test
    void aShotIntoAnOverlappingShipAndRockHitsTheShip() {
        World world = new World(GameMode.SOLO);
        EnemyShip enemy = new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 400, 300);
        Asteroid asteroid = new Asteroid(Sprite.ASTEROID_BIG, 400, 300, 40, 15, 20);
        world.addEnemy(enemy);
        world.addAsteroid(asteroid);
        world.addBullet(playerShotAt(world, 410, 310));

        system().resolve(world);

        assertEquals(EnemyShip.EnemyKind.FIGHTER.health() - 10, healthOf(enemy),
                "the ship the player was aiming at should take the hit");
        assertTrue(asteroid.isAlive());
        assertEquals(20, asteroid.scoreValue(), "the rock should be untouched");
    }

    @Test
    void aShotStopsAtTheFirstThingItHits() {
        World world = new World(GameMode.SOLO);
        EnemyShip near = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 400, 300);
        EnemyShip far = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 405, 305);
        world.addEnemy(near);
        world.addEnemy(far);
        world.addBullet(playerShotAt(world, 410, 310));

        system().resolve(world);

        int damaged = (healthOf(near) < EnemyShip.EnemyKind.SCOUT.health() ? 1 : 0)
                + (healthOf(far) < EnemyShip.EnemyKind.SCOUT.health() ? 1 : 0);
        assertEquals(1, damaged, "one bullet must not damage two ships");
    }

    @Test
    void aShotThatHitsNothingSurvivesTheScan() {
        World world = new World(GameMode.SOLO);
        world.addEnemy(new EnemyShip(EnemyShip.EnemyKind.FIGHTER, Sprite.L1_FIGHTER, 50, 50));
        world.addAsteroid(new Asteroid(Sprite.ASTEROID_BIG, 80, 80, 40, 15, 20));
        Bullet miss = playerShotAt(world, 700, 700);
        world.addBullet(miss);

        system().resolve(world);

        assertTrue(miss.isAlive());
    }

    @Test
    void anAlreadyDeadHazardIsSkippedRatherThanShotAgain() {
        World world = new World(GameMode.SOLO);
        EnemyShip corpse = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 400, 300);
        corpse.kill();
        world.addEnemy(corpse);
        Bullet shot = playerShotAt(world, 410, 310);
        world.addBullet(shot);

        system().resolve(world);

        assertTrue(shot.isAlive(), "a bullet must pass through something already destroyed");
        assertEquals(0, world.players().get(0).shotsHit());
    }

    @Test
    void aMissCostsNoRecordedHit() {
        World world = new World(GameMode.SOLO);
        world.addBullet(playerShotAt(world, 700, 700));

        system().resolve(world);

        assertEquals(0, world.players().get(0).shotsHit());
        assertFalse(world.players().get(0).isOut());
    }

    /**
     * Sitting inside a flagship costs one hit, not one per frame.
     *
     * A boss is never destroyed by a ram, so the overlap persists; without a grace window this
     * was sixty contact hits a second and an unavoidable death.
     */
    @Test
    void rammingABossDoesNotDealDamageEveryFrame() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        EnemyShip boss = new EnemyShip(com.hashimjacobs.spacecase.entity.Boss.SENTINEL,
                player.x() - 40, player.y() - 40);
        world.addEnemy(boss);
        int before = player.health();

        CollisionSystem system = system();
        for (int tick = 0; tick < 10; tick++) {
            system.resolve(world);
        }

        int lost = before - player.health();
        assertEquals(com.hashimjacobs.spacecase.GameConfig.ENEMY_CONTACT_DAMAGE, lost,
                "ten overlapping frames should cost one contact hit, not ten");
        assertTrue(boss.isAlive(), "a boss shrugs off the ram");
    }

    /** Health is private; a scout's remaining fraction against its archetype maximum stands in. */
    /**
     * The mega laser, which is the one weapon that does not stop at what it hits.
     *
     * Two enemies stacked in the same column both take the tick. A bullet fired at the same pair
     * would hit exactly one -- {@code aShotStopsAtTheFirstThingItHits} above pins that -- so this
     * is the difference between the beam and everything else, and the thing most likely to be
     * quietly broken by a change to the collision pass.
     */
    @Test
    void theBeamBurnsEverythingInTheLaneRatherThanStoppingAtTheFirst() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        EnemyShip near = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT,
                player.centerX() - 20, player.y() - 120);
        EnemyShip far = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT,
                player.centerX() - 20, player.y() - 300);
        world.addEnemy(near);
        world.addEnemy(far);
        player.collect(PowerUp.Kind.MEGA_LASER);
        player.setFiringBeam(true);

        system().resolve(world);

        int expected = EnemyShip.EnemyKind.SCOUT.health()
                - player.damageFor(GameConfig.BEAM_DAMAGE_PER_TICK);
        assertEquals(expected, healthOf(near), "the near enemy burns");
        assertEquals(expected, healthOf(far), "and so does the one behind it");
    }

    /** Nothing burns when the trigger is up, however long the pickup has been held. */
    @Test
    void anIdleBeamDamagesNothing() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        EnemyShip enemy = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT,
                player.centerX() - 20, player.y() - 120);
        world.addEnemy(enemy);
        player.collect(PowerUp.Kind.MEGA_LASER);

        system().resolve(world);

        assertEquals(EnemyShip.EnemyKind.SCOUT.health(), healthOf(enemy));
    }

    private static int healthOf(EnemyShip ship) {
        int max = ship.kind().health();
        return (int) Math.round(ship.remainingHealthFraction() * max);
    }
}
