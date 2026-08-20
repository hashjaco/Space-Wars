package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.entity.Rocket;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossAndDropsTest {

    /** Any level will do for firing patterns; it only decides which hull an escort wears. */
    private static final Level LEVEL = Level.values()[0];

    @Test
    void phaseFollowsRemainingHealth() {
        assertEquals(BossPhase.SPREAD, Boss.SENTINEL.phaseFor(1.0));
        assertEquals(BossPhase.SPREAD, Boss.SENTINEL.phaseFor(0.7));
        assertEquals(BossPhase.SWEEPING_FAN, Boss.SENTINEL.phaseFor(0.66));
        assertEquals(BossPhase.SWEEPING_FAN, Boss.SENTINEL.phaseFor(0.4));
        assertEquals(BossPhase.AIMED_BURST, Boss.SENTINEL.phaseFor(0.33));
        assertEquals(BossPhase.AIMED_BURST, Boss.SENTINEL.phaseFor(0.0));
    }

    @Test
    void everyBossChangesPatternAsItWeakens() {
        for (Boss boss : Boss.values()) {
            BossPhase opening = boss.phaseFor(1.0);
            BossPhase middle = boss.phaseFor(0.5);
            BossPhase last = boss.phaseFor(0.0);
            assertTrue(opening != middle || middle != last,
                    boss + " fights the same way throughout, so it is a sponge not a fight");
        }
    }

    @Test
    void theAimedPhaseIsMoreDangerousThanTheOpeningSpread() {
        assertTrue(BossPhase.AIMED_BURST.cooldownTicks() < BossPhase.SPREAD.cooldownTicks(),
                "the aimed phase should fire faster than a spread");
        assertTrue(BossPhase.SWEEPING_FAN.shots() > BossPhase.SPREAD.shots(),
                "a sweeping fan should put more bullets on screen than a spread");
    }

    @Test
    void aBossFiresAWholePatternWhileAScoutFiresOnce() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);

        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, LEVEL.enemySprite(EnemyShip.EnemyKind.SCOUT), 400, 200);
        EnemyWeapons.fire(world, scout, target, LEVEL);
        assertEquals(1, world.bullets().size(), "an ordinary enemy fires a single shot");

        world.bullets().clear();
        // Sentinel opens on SPREAD, so this reads the pattern rather than a spawner or a ring.
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, 90);
        EnemyWeapons.fire(world, boss, target, LEVEL);
        assertEquals(BossPhase.SPREAD.shots(), world.bullets().size());
    }

    @Test
    void theOpeningPatternSpreadsHorizontallyRatherThanFiringOneColumn() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, 90);

        EnemyWeapons.fire(world, boss, target, LEVEL);

        long movingLeft = world.bullets().stream().filter(b -> b.velocityX() < -0.01).count();
        long movingRight = world.bullets().stream().filter(b -> b.velocityX() > 0.01).count();
        assertTrue(movingLeft >= 1 && movingRight >= 1,
                "a spread must send bullets both ways, not straight down");
        for (Bullet bullet : world.bullets()) {
            assertTrue(bullet.velocityY() > 0, "enemy fire must travel down the arena");
        }
    }

    @Test
    void theFinalPhaseAimsAtThePlayer() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        // Put the player far to one side and the boss into its last phase.
        target.setPosition(80, 700);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 800, 90);
        boss.takeDamage((int) (Boss.SENTINEL.health() * 0.8));

        EnemyWeapons.fire(world, boss, target, LEVEL);

        double averageX = world.bullets().stream()
                .mapToDouble(Bullet::velocityX)
                .average()
                .orElseThrow();
        assertTrue(averageX < 0, "bullets should lead toward a player on the left; got " + averageX);
    }

    @Test
    void aRingPhaseCurtainsTheArenaWithoutFiringUpwards() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        // Foundry Warden's middle phase is the ring.
        EnemyShip boss = new EnemyShip(Boss.FOUNDRY_WARDEN, 400, 90);
        boss.takeDamage((int) (Boss.FOUNDRY_WARDEN.health() * 0.5));

        EnemyWeapons.fire(world, boss, target, LEVEL);

        assertEquals(BossPhase.RING.shots(), world.bullets().size());
        for (Bullet bullet : world.bullets()) {
            assertTrue(bullet.velocityY() >= 0,
                    "a ring fired from the top of the arena must not waste shots upward");
        }
    }

    @Test
    void aSpiralPhaseRotatesItsAimOverTime() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        // Void Weaver opens on the spiral.
        EnemyShip boss = new EnemyShip(Boss.VOID_WEAVER, 400, 90);

        EnemyWeapons.fire(world, boss, target, LEVEL);
        double firstShotAngle = world.bullets().get(0).velocityX();

        world.bullets().clear();
        for (int i = 0; i < 12; i++) {
            world.update();
        }
        EnemyWeapons.fire(world, boss, target, LEVEL);
        double laterShotAngle = world.bullets().get(0).velocityX();

        assertTrue(Math.abs(laterShotAngle - firstShotAngle) > 0.01,
                "a spiral must aim somewhere new as the clock advances");
    }

    @Test
    void aSpawnerPhaseCallsInEscortsInsteadOfFiring() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        // Scrap Hive opens on the spawner.
        EnemyShip boss = new EnemyShip(Boss.SCRAP_HIVE, 400, 90);
        world.addEnemy(boss);

        EnemyWeapons.fire(world, boss, target, LEVEL);

        assertEquals(0, world.bullets().size(), "a spawner fires nothing");
        assertTrue(world.enemies().size() > 1, "a spawner should add escorts to the arena");
    }

    @Test
    void aSpawnerStopsOnceTheArenaIsFull() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(Boss.SCRAP_HIVE, 400, 90);
        world.addEnemy(boss);

        for (int volley = 0; volley < 40; volley++) {
            EnemyWeapons.fire(world, boss, target, LEVEL);
        }

        assertTrue(world.enemies().size() <= 12,
                "escorts must be capped; got " + world.enemies().size());
    }

    @Test
    void drivingASpawnerBossDoesNotBreakTheEnemyLoop() {
        World world = new World(GameMode.SOLO);
        EnemyShip boss = new EnemyShip(Boss.SCRAP_HIVE, 400, 90);
        world.addEnemy(boss);
        PlayerShip target = world.players().get(0);

        // Mirrors GameLoop.driveEnemies: firing inside a walk of the enemy list must not trip a
        // ConcurrentModificationException when the boss adds escorts to that same list.
        for (int tick = 0; tick < 400; tick++) {
            java.util.List<EnemyShip> enemies = world.enemies();
            for (int i = 0, count = enemies.size(); i < count; i++) {
                // Through driveWeapons rather than fire, so the guard covers the rocket path too.
                EnemyWeapons.driveWeapons(world, enemies.get(i), target, LEVEL, 60,
                        SoundPlayer.SILENT);
            }
            world.update();
            world.sweep();
        }

        assertTrue(boss.isAlive(), "the boss should still be fighting");
    }

    @Test
    void aScaledFlagshipIsTougherThanAnAuthoredOne() {
        EnemyShip authored = new EnemyShip(Boss.SENTINEL, 400, 90);
        EnemyShip scaled = new EnemyShip(Boss.SENTINEL, 400, 90, 2.0);

        assertEquals(1.0, authored.scale());
        assertEquals(2.0, scaled.scale());

        // Same proportional damage takes twice as many points off the scaled one.
        authored.takeDamage(Boss.SENTINEL.health());
        assertTrue(!authored.isAlive());
        scaled.takeDamage(Boss.SENTINEL.health());
        assertTrue(scaled.isAlive(), "twice the health should survive one authored health bar");
    }

    @Test
    void aScaledFlagshipFiresFaster() {
        EnemyShip authored = new EnemyShip(Boss.SENTINEL, 400, 90);
        EnemyShip scaled = new EnemyShip(Boss.SENTINEL, 400, 90, 2.0);

        int slow = EnemyWeapons.cooldownFor(authored, 60);
        int fast = EnemyWeapons.cooldownFor(scaled, 60);

        assertTrue(fast < slow, "scaling should shorten the gap between volleys");
        assertTrue(fast >= 4, "but never below the floor that keeps a pattern readable");
    }

    @Test
    void anOrdinaryEnemyIsNeverScaled() {
        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 100, 100);

        assertEquals(1.0, scout.scale());
        assertEquals(60, EnemyWeapons.cooldownFor(scout, 60), "scouts use the difficulty cooldown");
    }

    @Test
    void aFlagshipEventuallyLaunchesTrackingRockets() {
        World world = new World(GameMode.SOLO);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, 90);
        world.addEnemy(boss);
        PlayerShip target = world.players().get(0);

        boolean sawRocket = false;
        for (int tick = 0; tick < 600 && !sawRocket; tick++) {
            EnemyWeapons.driveWeapons(world, boss, target, LEVEL, 60, SoundPlayer.SILENT);
            sawRocket = world.bullets().stream().anyMatch(b -> b instanceof Rocket);
        }

        assertTrue(sawRocket, "the secondary weapon should fire within ten seconds");
    }

    @Test
    void rocketsHitHarderAndFlySlowerThanOrdinaryBossFire() {
        World world = new World(GameMode.SOLO);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, 90);
        world.addEnemy(boss);
        PlayerShip target = world.players().get(0);

        for (int tick = 0; tick < 600; tick++) {
            EnemyWeapons.driveWeapons(world, boss, target, LEVEL, 60, SoundPlayer.SILENT);
        }

        Bullet rocket = world.bullets().stream()
                .filter(b -> b instanceof Rocket)
                .findFirst()
                .orElseThrow();

        assertTrue(rocket.damage() > GameConfig.ENEMY_BULLET_DAMAGE);
        assertTrue(Math.hypot(rocket.velocityX(), rocket.velocityY())
                < GameConfig.ENEMY_BULLET_SPEED, "a rocket is the slow, heavy option");
    }

    /** A spawner is venting escorts, not shooting, so it must not be firing rockets either way. */
    @Test
    void anOrdinaryEnemyNeverLaunchesRockets() {
        World world = new World(GameMode.SOLO);
        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, Sprite.L1_SCOUT, 400, 100);
        world.addEnemy(scout);
        PlayerShip target = world.players().get(0);

        for (int tick = 0; tick < 600; tick++) {
            EnemyWeapons.driveWeapons(world, scout, target, LEVEL, 60, SoundPlayer.SILENT);
        }

        assertTrue(world.bullets().stream().noneMatch(b -> b instanceof Rocket));
        assertTrue(world.bullets().size() > 0, "it should still be firing ordinary shots");
    }

    @Test
    void theBossHoldsStationInsteadOfDriftingOffTheBottom() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, -190);
        world.addEnemy(boss);

        for (int i = 0; i < 2000; i++) {
            boss.trackAcross(target);
            world.update();
            world.sweep();
        }

        assertTrue(boss.isAlive(), "the boss must not fly out of the arena and be culled");
        assertTrue(boss.y() < 200, "the boss should hold near the top; was at " + boss.y());
    }

    @Test
    void anUndrivenBossStillHoldsStation() {
        World world = new World(GameMode.SOLO);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, -190);
        world.addEnemy(boss);

        // Never tracked, which is what happens when no player is left alive to aim at. The station
        // clamp has to hold anyway: a boss culled off the bottom would hand out a free level.
        for (int i = 0; i < 4000; i++) {
            world.update();
            world.sweep();
        }

        assertTrue(boss.isAlive(), "an undriven boss must not leave the arena and be culled");
        assertTrue(world.bossPresent(), "the world should still report a boss on the field");
    }

    @Test
    void adefeatedBossLeavesSeveralPickups() {
        World world = new World(GameMode.SOLO);
        PlayerShip shooter = world.players().get(0);
        EnemyShip boss = new EnemyShip(Boss.SENTINEL, 400, 200);
        world.addEnemy(boss);

        killWith(world, shooter, boss, Boss.SENTINEL.health());

        assertTrue(world.powerUps().size() >= 2,
                "a boss should be worth several pickups; got " + world.powerUps().size());
    }

    @Test
    void ordinaryEnemiesSometimesDropAPickup() {
        int totalDrops = 0;
        for (int seed = 0; seed < 40; seed++) {
            World world = new World(GameMode.SOLO);
            PlayerShip shooter = world.players().get(0);
            EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, LEVEL.enemySprite(EnemyShip.EnemyKind.SCOUT), 400, 200);
            world.addEnemy(scout);

            CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, new Random(seed));
            world.addBullet(new Bullet(Sprite.PLAYER_BULLET, scout.centerX(), scout.centerY(),
                    0, 0, shooter, 999));
            collisions.resolve(world);

            totalDrops += world.powerUps().size();
        }
        assertTrue(totalDrops > 0, "enemies should drop pickups at least sometimes");
        assertTrue(totalDrops < 40, "not every enemy should drop; drops were " + totalDrops);
    }

    /**
     * The beam is restricted loot, and this is the assertion that keeps it that way.
     *
     * A scout dropping mega lasers at the same rate as health packs is the single change that
     * would trivialise the game, and it is one careless {@code Kind.values()} away.
     */
    @Test
    void lightEnemiesNeverDropTheBeamButHeavyOnesSometimesDo() {
        int fromScouts = beamDropsOver(EnemyShip.EnemyKind.SCOUT, 400);
        int fromFighters = beamDropsOver(EnemyShip.EnemyKind.FIGHTER, 400);
        int fromCruisers = beamDropsOver(EnemyShip.EnemyKind.CRUISER, 400);

        assertEquals(0, fromScouts, "scouts must never carry a beam");
        assertEquals(0, fromFighters, "nor fighters");
        assertTrue(fromCruisers > 0, "cruisers should sometimes carry one");
    }

    /** And when a heavy hull does drop one, it is rarer than any of the ordinary pickups. */
    @Test
    void theBeamIsRarerThanAnOrdinaryPickup() {
        int beams = 0;
        int total = 0;
        for (int seed = 0; seed < 600; seed++) {
            World world = killedBy(EnemyShip.EnemyKind.CRUISER, seed);
            for (PowerUp powerUp : world.powerUps()) {
                total++;
                if (powerUp.kind() == PowerUp.Kind.MEGA_LASER) {
                    beams++;
                }
            }
        }
        assertTrue(total > 0, "the sample needs some drops in it");
        // One in six is what an even split over the six common pickups would give each of them.
        assertTrue(beams < total / 6.0,
                "beams were " + beams + " of " + total + " drops, which is not rarer than the rest");
    }

    private static int beamDropsOver(EnemyShip.EnemyKind kind, int seeds) {
        int beams = 0;
        for (int seed = 0; seed < seeds; seed++) {
            for (PowerUp powerUp : killedBy(kind, seed).powerUps()) {
                if (powerUp.kind() == PowerUp.Kind.MEGA_LASER) {
                    beams++;
                }
            }
        }
        return beams;
    }

    /** One enemy of this kind, shot dead, and whatever it left behind. */
    private static World killedBy(EnemyShip.EnemyKind kind, int seed) {
        World world = new World(GameMode.SOLO);
        PlayerShip shooter = world.players().get(0);
        EnemyShip enemy = new EnemyShip(kind, LEVEL.enemySprite(kind), 400, 200);
        world.addEnemy(enemy);
        world.addBullet(new Bullet(Sprite.PLAYER_BULLET, enemy.centerX(), enemy.centerY(),
                0, 0, shooter, 999));
        new CollisionSystem(SoundPlayer.SILENT, new Random(seed)).resolve(world);
        return world;
    }

    @Test
    void battleModeStillDropsPickupsFromTheSkySinceItHasNoEnemies() {
        World world = new World(GameMode.BATTLE);
        SpawnDirector director = new SpawnDirector(
                new Random(4), com.hashimjacobs.spacecase.prefs.Difficulty.NORMAL,
                GameMode.BATTLE.rules());

        for (int i = 0; i < 4000; i++) {
            director.update(world);
        }

        assertTrue(world.powerUps().size() > 0,
                "battle mode has no enemies to drop pickups, so they must fall ambiently");
    }

    @Test
    void soloModeDoesNotRainPickupsFromEmptySky() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(4), com.hashimjacobs.spacecase.prefs.Difficulty.NORMAL,
                GameMode.SOLO.rules());

        for (int i = 0; i < 4000; i++) {
            director.update(world);
        }

        assertEquals(0, world.powerUps().size(),
                "with enemies present, pickups should come from kills instead");
    }

    private static void killWith(World world, PlayerShip shooter, EnemyShip enemy, int damage) {
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, new Random(1));
        world.addBullet(new Bullet(Sprite.PLAYER_BULLET, enemy.centerX(), enemy.centerY(),
                0, 0, shooter, damage));
        collisions.resolve(world);
    }
}
