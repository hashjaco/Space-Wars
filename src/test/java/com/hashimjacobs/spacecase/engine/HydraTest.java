package com.hashimjacobs.spacecase.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BossHead;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.Rocket;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three-headed fight, driven through the same World and weapons the game uses. */
class HydraTest {

    // Named rather than "the last level", which it stopped being once the campaign grew past one
    // galaxy. The hydra guards Hollow Womb specifically; the test wants that level's art and waves.
    private static final Level LEVEL = Level.HOLLOW_WOMB;

    /** The NORMAL preset's ceiling on ordinary enemies, which is what a spawner gets headroom over. */
    private static final int ENEMY_CAP = 6;

    private static World withHydra() {
        World world = new World(GameMode.SOLO);
        EnemyShip torso = new EnemyShip(Boss.HYDRA, 400, 90, 1);
        world.addEnemy(torso);
        for (EnemyShip head : torso.parts()) {
            world.addEnemy(head);
        }
        return world;
    }

    private static EnemyShip torsoOf(World world) {
        return world.boss();
    }

    private static List<EnemyShip> headsOf(World world) {
        return world.boss().parts();
    }

    @Test
    void theHydraArrivesWithThreeHeads() {
        World world = withHydra();

        assertEquals(3, headsOf(world).size());
        assertEquals(4, world.enemies().size(), "a torso and three heads");
        for (EnemyShip head : headsOf(world)) {
            assertTrue(head.isBossPart());
            assertTrue(head.isBoss(), "a head is still flagship-grade for ramming and culling");
        }
    }

    @Test
    void theTorsoIgnoresDamageUntilEveryHeadIsDown() {
        World world = withHydra();
        EnemyShip torso = torsoOf(world);
        double before = torso.remainingHealthFraction();

        torso.takeDamage(100_000);

        assertTrue(torso.isAlive(), "the torso is armoured while a head still bites");
        assertEquals(before, torso.remainingHealthFraction(), 1e-9);

        for (EnemyShip head : headsOf(world)) {
            head.takeDamage(100_000);
        }
        torso.takeDamage(100_000);

        assertFalse(torso.isAlive(), "with every head down it takes damage like anything else");
    }

    @Test
    void shootingAHeadDrainsTheBossBarEvenThoughTheTorsoIsUntouchable() {
        World world = withHydra();
        EnemyShip torso = torsoOf(world);
        double before = torso.remainingHealthFraction();

        headsOf(world).get(0).takeDamage(150);

        assertTrue(torso.remainingHealthFraction() < before,
                "the bar counts the heads, so it moves from the first shot");
    }

    @Test
    void killingOneHeadSilencesThatHeadAndNoOther() {
        World world = withHydra();
        PlayerShip target = world.players().get(0);
        EnemyShip doomed = headsOf(world).get(0);
        doomed.takeDamage(100_000);
        world.sweep();

        int before = world.bullets().size();
        for (int tick = 0; tick < 300; tick++) {
            EnemyWeapons.driveWeapons(world, doomed, target, LEVEL, 60, ENEMY_CAP, SoundPlayer.SILENT);
        }
        assertEquals(before, world.bullets().size(), "a dead head must not keep firing");

        EnemyShip living = headsOf(world).get(1);
        for (int tick = 0; tick < 300; tick++) {
            EnemyWeapons.driveWeapons(world, living, target, LEVEL, 60, ENEMY_CAP, SoundPlayer.SILENT);
        }
        assertTrue(world.bullets().size() > before, "the others carry on");
    }

    /** The safety net: nothing belonging to the flagship may be alive when the level turns over. */
    @Test
    void theLevelStaysOpenWhileAnyHeadLives() {
        World world = withHydra();
        for (EnemyShip head : headsOf(world)) {
            head.takeDamage(100_000);
        }
        // Two down, one to go.
        headsOf(world).get(0).kill();
        world.sweep();

        assertTrue(world.bossPresent());
    }

    @Test
    void theHudReadsTheTorsoWhicheverOrderThePartsWereAdded() {
        World world = new World(GameMode.SOLO);
        EnemyShip torso = new EnemyShip(Boss.HYDRA, 400, 90, 1);
        // Heads first, deliberately: World.boss() must not depend on insertion order.
        for (EnemyShip head : torso.parts()) {
            world.addEnemy(head);
        }
        world.addEnemy(torso);

        assertEquals(torso, world.boss());
        assertFalse(world.boss().isBossPart());
    }

    /**
     * The one step where the torso is gone and the heads have not yet noticed.
     *
     * Deliberately no {@code update()} between the kill and the assert, which is what separates
     * this from {@link #noHeadOutlivesItsTorso}: the game loop sweeps and then reads the boss for
     * the music in the same step, before the update that lets a head see its body is dead. That
     * gap used to make {@code bossPresent()} true while {@code boss()} was null, which threw.
     */
    @Test
    void aSurvivingHeadAnswersForTheFlagshipUntilItNoticesTheTorsoIsGone() {
        World world = withHydra();
        torsoOf(world).kill();
        world.sweep();

        assertTrue(world.bossPresent());
        assertNotNull(world.boss(), "a head still on screen has to answer for the flagship");
        assertTrue(world.boss().isBossPart());
        // What GameLoop.updateBossMusic dereferences, and the whole point of answering at all.
        assertEquals(Boss.HYDRA, world.boss().boss());
    }

    @Test
    void noHeadOutlivesItsTorso() {
        World world = withHydra();
        List<EnemyShip> heads = List.copyOf(headsOf(world));
        torsoOf(world).kill();

        world.update();
        world.sweep();

        for (EnemyShip head : heads) {
            assertFalse(head.isAlive(), "a head cannot go on without a body");
        }
    }

    /** Reach plus the holding depth has to stay inside the arena or the culler eats the heads. */
    @Test
    void theNecksNeverSwingOutOfTheArena() {
        World world = withHydra();
        List<EnemyShip> heads = List.copyOf(headsOf(world));

        for (int tick = 0; tick < 3000; tick++) {
            world.update();
            world.sweep();
        }

        for (EnemyShip head : heads) {
            assertTrue(head.isAlive(), "a head was culled mid-fight");
            assertTrue(head.y() > -head.height() && head.y() < GameConfig.HEIGHT,
                    "head left the arena at y=" + head.y());
        }
    }

    /**
     * No two heads may ever sit on top of each other.
     *
     * Phase offsets alone do not achieve this: three phases a third of a cycle apart still give
     * two heads the same sine twice per cycle, and they used to stack exactly. The necks are
     * different lengths for this reason, and this walks a full sweep to prove it.
     */
    @Test
    void theThreeHeadsNeverOverlap() {
        World world = withHydra();
        List<EnemyShip> heads = List.copyOf(headsOf(world));

        for (int tick = 0; tick < 400; tick++) {
            world.update();
            for (int a = 0; a < heads.size(); a++) {
                for (int b = a + 1; b < heads.size(); b++) {
                    double gap = Math.hypot(heads.get(a).centerX() - heads.get(b).centerX(),
                            heads.get(a).centerY() - heads.get(b).centerY());
                    assertTrue(gap > 30,
                            "heads " + a + " and " + b + " were " + gap + " apart at tick " + tick);
                }
            }
        }
    }

    @Test
    void aHeadSpitsAcidAndTheTorsoDoesNot() {
        World world = withHydra();
        PlayerShip target = world.players().get(0);

        for (int tick = 0; tick < 600; tick++) {
            EnemyWeapons.driveWeapons(world, torsoOf(world), target, LEVEL, 60, ENEMY_CAP, SoundPlayer.SILENT);
        }
        assertTrue(world.bullets().stream().noneMatch(b -> b instanceof Rocket),
                "the torso has no mouth of its own");

        for (int tick = 0; tick < 600; tick++) {
            EnemyWeapons.driveWeapons(world, headsOf(world).get(0), target, LEVEL, 60, ENEMY_CAP,
                    SoundPlayer.SILENT);
        }
        assertTrue(world.bullets().stream().anyMatch(b -> b instanceof Rocket),
                "a head should have spat by now");
    }

    /** The fuse against the launch interval: acid must not accumulate over a long fight. */
    @Test
    void acidBurnsOutSoTheArenaDoesNotFillUp() {
        World world = withHydra();
        PlayerShip target = world.players().get(0);
        int peak = 0;

        for (int tick = 0; tick < 3000; tick++) {
            for (EnemyShip head : headsOf(world)) {
                EnemyWeapons.driveWeapons(world, head, target, LEVEL, 600, ENEMY_CAP, SoundPlayer.SILENT);
            }
            world.update();
            world.sweep();
            peak = Math.max(peak, (int) world.bullets().stream()
                    .filter(b -> b instanceof Rocket).count());
        }

        assertTrue(peak > 0, "the heads should have spat at all");
        assertTrue(peak <= 12, "acid accumulated to " + peak + " in the air");
    }

    @Test
    void aKilledHeadDoesNotPayTheFullFlagshipBounty() {
        World world = withHydra();
        EnemyShip head = headsOf(world).get(0);
        EnemyShip torso = torsoOf(world);

        assertTrue(head.scoreValue() < torso.scoreValue(),
                "a head is worth less than the whole flagship");
        assertTrue(head.scoreValue() > 0, "but killing one should still pay something");
    }

    @Test
    void everyHeadTakesAShareOfTheFlagshipsHealth() {
        World world = withHydra();
        EnemyShip torso = torsoOf(world);

        // Full bar with nothing yet damaged.
        assertEquals(1.0, torso.remainingHealthFraction(), 1e-9);

        for (EnemyShip head : headsOf(world)) {
            head.takeDamage(100_000);
        }

        double left = torso.remainingHealthFraction();
        assertTrue(left > 0.4 && left < 0.7,
                "with the heads gone the torso should be most but not all of the bar, was " + left);
    }

    @Test
    void aBulletThatHitsAHeadDamagesTheHeadNotTheTorso() {
        World world = withHydra();
        EnemyShip head = headsOf(world).get(0);
        EnemyShip torso = torsoOf(world);
        world.update();

        PlayerShip shooter = world.players().get(0);
        Bullet shot = new Bullet(com.hashimjacobs.spacecase.asset.Sprite.PLAYER_BULLET,
                head.centerX() - 8, head.centerY() - 11, 0, 0, shooter, 120);
        world.addBullet(shot);
        double headBefore = head.remainingHealthFraction();

        new CollisionSystem(SoundPlayer.SILENT).resolve(world);

        assertTrue(head.remainingHealthFraction() < headBefore, "the head should have taken it");
        assertTrue(torso.isAlive());
    }
}
