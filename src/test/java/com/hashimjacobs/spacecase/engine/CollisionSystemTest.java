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
     * The mega laser stops at the first hull in its lane, and the hull shields what is behind it.
     *
     * It used to pierce, and the pierce was the weapon's whole identity -- but it was also drawn
     * nose-to-wall through whatever it met, which read as the beam missing the thing it was in fact
     * killing. Stopping is what buys the impact.
     *
     * Both halves matter and both are here. The near enemy still burns for a full tick -- a beam
     * that stopped <em>short</em> of what it hit would pass a test that only checked the far one --
     * and the far enemy takes nothing at all.
     */
    @Test
    void theBeamStopsAtTheFirstThingItHits() {
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

        int burnt = EnemyShip.EnemyKind.SCOUT.health()
                - player.damageFor(GameConfig.BEAM_DAMAGE_PER_TICK);
        assertEquals(burnt, healthOf(near), "the near enemy burns");
        assertEquals(EnemyShip.EnemyKind.SCOUT.health(), healthOf(far),
                "the one behind it is shielded by the one in front");
        assertTrue(player.beamStopped(), "and the beam knows it landed, so the renderer can bloom it");
    }

    /** A clear lane is still a full-length beam: the stop is a clamp, not a new default. */
    @Test
    void aBeamWithNothingInItStillReachesTheWall() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        player.collect(PowerUp.Kind.MEGA_LASER);
        player.setFiringBeam(true);

        system().resolve(world);

        assertFalse(player.beamStopped(), "nothing was in the lane");
        assertEquals(0, player.beamBox()[1], "the beam should run to the top wall");
    }

    /**
     * The scythe goes through a column and burns every hull in it exactly once.
     *
     * Both halves matter. A blade that stopped would be a slow bullet, and one that did not remember
     * what it had already cut would burn the first ship sixty times a second on its way past --
     * which is one line away from the first and invisible in a screenshot.
     */
    @Test
    void aPiercingRoundCutsEveryHullOnceAndKeepsGoing() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        EnemyShip near = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER, 400, 300);
        EnemyShip far = new EnemyShip(EnemyShip.EnemyKind.CRUISER, Sprite.L1_CRUISER, 400, 260);
        world.addEnemy(near);
        world.addEnemy(far);
        Bullet blade = new Bullet(Sprite.SCYTHE_BLADE, 400, 305, 0, -4, player, 7).piercing();
        world.addBullet(blade);

        CollisionSystem system = system();
        system.resolve(world);
        system.resolve(world);

        assertTrue(blade.isAlive(), "a piercing round must not die on what it hits");
        int max = EnemyShip.EnemyKind.CRUISER.health();
        assertEquals(max - 7, healthOf(near), "the near hull is cut once, not once a tick");
        assertEquals(max - 7, healthOf(far), "and the one behind it is cut too");
    }

    /** A fused round is a range limit: it expires on its own rather than leaving through the wall. */
    @Test
    void aFusedRoundExpiresWhereItsRangeRunsOut() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        Bullet pellet = new Bullet(Sprite.FLAK_PELLET, 400, 300, 0, -13, player, 9).withFuse(3);
        world.addBullet(pellet);

        for (int tick = 0; tick < 3; tick++) {
            assertTrue(pellet.isAlive(), "the pellet died before its fuse ran out");
            pellet.update();
        }

        assertFalse(pellet.isAlive(), "the pellet outlived its fuse");
    }

    /**
     * The nova answers for a radius, and for one explosion rather than one per victim.
     *
     * That second half is why the count is asserted: {@code World.addExplosion} kicks the camera on
     * every call, so a blast that spawned one per kill would shake the screen once per ship in it.
     */
    @Test
    void aDetonatingRoundBurnsEverythingInItsRadiusAndShakesOnce() {
        World world = new World(GameMode.SOLO);
        PlayerShip player = world.players().get(0);
        EnemyShip hit = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 400, 300);
        EnemyShip beside = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 460, 300);
        EnemyShip clear = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 900, 100);
        world.addEnemy(hit);
        world.addEnemy(beside);
        world.addEnemy(clear);
        world.addBullet(new Bullet(Sprite.NOVA_SHELL, 405, 305, 0, -1, player, 5).detonating(120));

        system().resolve(world);

        assertFalse(hit.isAlive(), "the shell's own target should be inside its blast");
        assertFalse(beside.isAlive(), "so should the one standing next to it");
        assertEquals(EnemyShip.EnemyKind.SCOUT.health(), healthOf(clear),
                "a ship across the arena is not in a 120px radius");
        // Two kills each explode through awardKill as any kill does, and the blast adds exactly one
        // more. Four would mean the blast was spawning one per victim.
        assertEquals(3, world.explosions().size(), "the blast must add one explosion, not one each");
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
