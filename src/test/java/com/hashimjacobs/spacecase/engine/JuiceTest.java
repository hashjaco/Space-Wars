package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.asset.SoundPlayer;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feel layer, pinned the way {@link ScreenShakeTest} pins the shake: pure arithmetic on
 * {@link World} and the entities, no toolkit, nothing read from the shared Random.
 */
class JuiceTest {

    private static final Level LEVEL = Level.values()[0];

    @Test
    void hitStopHoldsEverythingButTheClockAndFoldsIntoTheChecksum() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(LEVEL);
        Bullet shot = new Bullet(Sprite.PLAYER_BULLET, 100, 100, 0, -5, world.players().get(0), 1);
        world.addBullet(shot);
        long still = world.checksum();

        world.freeze(3);
        assertTrue(world.frozen());
        assertNotEquals(still, world.checksum(), "a held world must not fingerprint like a free one");
        int tick = world.tick();
        for (int i = 0; i < 3; i++) {
            world.update();
        }
        assertEquals(100, shot.y(), 1e-9, "nothing moves while the world is held");
        assertEquals(tick + 3, world.tick(), "the clock keeps counting through the hold");
        assertFalse(world.frozen());
        world.update();
        assertEquals(95, shot.y(), 1e-9, "and the hold ends exactly when it says it does");

        world.freeze(2);
        world.freeze(5);
        world.freeze(1);
        for (int i = 0; i < 4; i++) {
            world.update();
        }
        assertTrue(world.frozen(), "the longer hold wins over a shorter one asked for later");
    }

    @Test
    void sparksBurnOutAndAreSwept() {
        World world = new World(GameMode.SOLO);
        world.emit(Particle.Kind.SPARK, 200, 200, 10, 6);
        assertEquals(10, world.particles().size());
        double x0 = world.particles().get(0).x();
        world.tickScenery();
        assertNotEquals(x0, world.particles().get(0).x(), "a spark flies");
        for (int i = 0; i < 40; i++) {
            world.tickScenery();
        }
        world.sweep();
        assertTrue(world.particles().isEmpty(), "spent sparks are swept like finished explosions");
    }

    @Test
    void sparksAreDeterministicFromTickAndPlaceAlone() {
        World a = new World(GameMode.SOLO);
        World b = new World(GameMode.SOLO);
        a.emit(Particle.Kind.SPARK, 10, 20, 5, 4);
        b.emit(Particle.Kind.SPARK, 10, 20, 5, 4);
        for (int i = 0; i < 5; i++) {
            assertEquals(a.particles().get(i).x(), b.particles().get(i).x(), 1e-12);
            assertEquals(a.particles().get(i).y(), b.particles().get(i).y(), 1e-12);
        }
        assertEquals(a.checksum(), b.checksum());
        a.emit(Particle.Kind.EMBER, 10, 20, 50, 4);
        assertEquals(a.checksum(), b.checksum(), "decoration never reaches the fingerprint");
    }

    @Test
    void bookedExplosionsFireOnTheirTick() {
        World world = new World(GameMode.SOLO);
        world.scheduleExplosion(3, 100, 100, 80);
        world.tickScenery();
        world.tickScenery();
        assertTrue(world.explosions().isEmpty(), "not yet");
        world.tickScenery();
        assertEquals(1, world.explosions().size(), "on the booked tick");
        assertTrue(world.shakeRemaining() > 0, "and it kicks the camera like any other blast");
    }

    @Test
    void killsInQuickSuccessionBuildAMultiplierThatCaps() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(LEVEL);
        PlayerShip pilot = world.players().get(0);
        pilot.addKillScore(100, 10);
        assertEquals(100, pilot.score(), "the first kill pays face value");
        pilot.addKillScore(100, 50);
        assertEquals(210, pilot.score(), "the second, inside the window, pays a tenth more");
        for (int tick = 60; tick < 60 + 10 * 30; tick += 30) {
            pilot.addKillScore(100, tick);
        }
        assertEquals(1.5, pilot.comboMultiplier(), 1e-9, "and it never climbs past one and a half");
        pilot.addKillScore(100, 10_000);
        assertEquals(1, pilot.combo(), "a pause resets the chain");
        assertEquals(1.0, pilot.comboMultiplier(), 1e-9);
    }

    @Test
    void aHitLightsTheHullForAFewTicks() {
        EnemyShip scout = new EnemyShip(EnemyShip.EnemyKind.SCOUT, LEVEL.enemySprite(EnemyShip.EnemyKind.SCOUT), 0, 0);
        assertFalse(scout.hitFlashing());
        scout.takeDamage(1);
        assertTrue(scout.hitFlashing());
        for (int i = 0; i < 4; i++) {
            scout.tickTimers();
        }
        assertFalse(scout.hitFlashing(), "four ticks: the hit, not the health");
    }

    @Test
    void nullsFlagshipsBreakRhythmWithTheLattice() {
        Boss nullOpener = Boss.values()[Galaxy.LEVELS_PER_GALAXY * 4];
        assertEquals(BossPhase.LATTICE, nullOpener.special());
        assertTrue(BossPhase.LATTICE.isSpecial());
        assertEquals(8, BossPhase.LATTICE.shots());

        World world = new World(GameMode.SOLO);
        PlayerShip target = world.players().get(0);
        EnemyShip boss = new EnemyShip(nullOpener, 400, 90);
        boolean seen = false;
        for (int tick = 0; tick < 1200 && !seen; tick++) {
            world.bullets().clear();
            EnemyWeapons.driveWeapons(world, boss, target, LEVEL, 60, 8, SoundPlayer.SILENT);
            if (boss.specialVolleyNow()) {
                seen = true;
                assertEquals(BossPhase.LATTICE.shots(), world.bullets().size(), "a special volley is the lattice");
            }
        }
        assertTrue(seen, "the special should come within twenty seconds");
    }
}
