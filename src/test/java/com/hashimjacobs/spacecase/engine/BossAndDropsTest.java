package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossAndDropsTest {

    @Test
    void phaseFollowsRemainingHealth() {
        assertEquals(BossPhase.SPREAD, BossPhase.forHealthFraction(1.0));
        assertEquals(BossPhase.SPREAD, BossPhase.forHealthFraction(0.7));
        assertEquals(BossPhase.SWEEPING_FAN, BossPhase.forHealthFraction(0.66));
        assertEquals(BossPhase.SWEEPING_FAN, BossPhase.forHealthFraction(0.4));
        assertEquals(BossPhase.AIMED_BURST, BossPhase.forHealthFraction(0.33));
        assertEquals(BossPhase.AIMED_BURST, BossPhase.forHealthFraction(0.0));
    }

    @Test
    void theBossGetsMoreDangerousAsItWeakens() {
        assertTrue(BossPhase.AIMED_BURST.cooldownTicks() < BossPhase.SPREAD.cooldownTicks(),
                "the final phase should fire faster than the opening one");
        assertTrue(BossPhase.SWEEPING_FAN.shots() > BossPhase.SPREAD.shots(),
                "the middle phase should put more bullets on screen");
    }

    @Test
    void aBossFiresAWholePatternWhileAScoutFiresOnce() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);

        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, 400, 200);
        EnemyWeapons.fire(world, scout, target);
        assertEquals(1, world.bullets().size(), "an ordinary enemy fires a single shot");

        world.bullets().clear();
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, 400, 90);
        EnemyWeapons.fire(world, boss, target);
        assertEquals(BossPhase.SPREAD.shots(), world.bullets().size());
    }

    @Test
    void theOpeningPatternSpreadsHorizontallyRatherThanFiringOneColumn() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, 400, 90);

        EnemyWeapons.fire(world, boss, target);

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
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, 800, 90);
        boss.takeDamage((int) (EnemyShip.EnemyKind.BOSS.health() * 0.8));

        EnemyWeapons.fire(world, boss, target);

        double averageX = world.bullets().stream()
                .mapToDouble(Bullet::velocityX)
                .average()
                .orElseThrow();
        assertTrue(averageX < 0, "bullets should lead toward a player on the left; got " + averageX);
    }

    @Test
    void theBossHoldsStationInsteadOfDriftingOffTheBottom() {
        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, 400, -190);
        world.addEnemy(boss);

        for (int i = 0; i < 2000; i++) {
            boss.trackHorizontally(target);
            world.update();
            world.sweep();
        }

        assertTrue(boss.isAlive(), "the boss must not fly out of the arena and be culled");
        assertTrue(boss.y() < 200, "the boss should hold near the top; was at " + boss.y());
    }

    @Test
    void adefeatedBossLeavesSeveralPickups() {
        World world = new World(GameMode.SOLO);
        PlayerShip shooter = world.players().get(0);
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, 400, 200);
        world.addEnemy(boss);

        killWith(world, shooter, boss, EnemyShip.EnemyKind.BOSS.health());

        assertTrue(world.powerUps().size() >= 2,
                "a boss should be worth several pickups; got " + world.powerUps().size());
    }

    @Test
    void ordinaryEnemiesSometimesDropAPickup() {
        int totalDrops = 0;
        for (int seed = 0; seed < 40; seed++) {
            World world = new World(GameMode.SOLO);
            PlayerShip shooter = world.players().get(0);
            EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, 400, 200);
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
