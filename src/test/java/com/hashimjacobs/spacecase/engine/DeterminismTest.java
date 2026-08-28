package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fight must be a pure function of (seed, inputs). Networked play rests entirely on this.
 *
 * Every peer will run its own copy of the simulation and only exchange input, so two machines
 * stepping the same ticks from the same seed have to arrive at the same world or they are playing
 * different games. This pins the half of that which can be checked inside one JVM: hidden global
 * state, an unseeded generator, a wall-clock read, a dependence on iteration order.
 *
 * What it cannot see is the other half. Both runs here execute on the same JVM on the same CPU, so
 * a {@link Math#sin} that answers differently on ARM than on x86 is invisible to it. That is what
 * {@code -Dprint.checksum} is for: print the number, run the suite on two platforms, compare. Only
 * a disagreement there justifies the StrictMath sweep.
 */
class DeterminismTest {

    private static final int TICKS = 3000;

    /**
     * Deliberately not built from {@link com.hashimjacobs.spacecase.prefs.Settings}, which would
     * read a real preferences store -- and which is exactly the per-machine input the networked
     * design has to keep out of the simulation.
     */
    private static final PlayerControls BOUND_KEYS = new PlayerControls(
            Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A), Set.of(KeyCode.D),
            Set.of(KeyCode.SPACE));

    @Test
    void theSameSeedAndTheSameInputsProduceTheSameFight() {
        long checksum = run(1234);
        assertEquals(checksum, run(1234), "the fight must be reproducible tick for tick");
        report(checksum);
    }

    /**
     * The cross-platform half of the check, which no assertion in this file can make.
     *
     * Printed rather than pinned to a constant. A pinned number would fail the build every time
     * anyone tuned a weapon or a spawn rate, which is a change to the game rather than to its
     * determinism -- and the suite would then be trained to ignore it. Run
     * {@code mvn test -Dprint.checksum} on two platforms and compare the two lines by eye; only a
     * disagreement means the Math-to-StrictMath sweep is actually needed.
     */
    private static void report(long checksum) {
        if (System.getProperty("print.checksum") != null) {
            System.out.printf("checksum seed=1234 ticks=%d os=%s arch=%s java=%s -> %d%n",
                    TICKS, System.getProperty("os.name"), System.getProperty("os.arch"),
                    System.getProperty("java.version"), checksum);
        }
    }

    /**
     * Not padding. Without it, a checksum that folded nothing -- or that a later edit reduced to a
     * constant -- would satisfy the test above forever.
     */
    @Test
    void aDifferentSeedProducesADifferentFight() {
        assertNotEquals(run(1234), run(5678), "the checksum must actually be sensitive");
    }

    @Test
    void theFightIsBusyEnoughToBeWorthHashing() {
        World world = new World(GameMode.COOP);
        long checksum = simulate(world, 1234);

        assertTrue(world.tick() == TICKS, "the world should have stepped every tick");
        assertTrue(world.enemies().size() + world.asteroids().size() > 0,
                "3000 ticks with nothing in the sky would prove nothing about determinism");
        assertNotEquals(0, checksum, "a zero checksum means the fold never ran");
    }

    /** The rolling hash of one whole fight, which is what two machines would compare. */
    private static long run(long seed) {
        return simulate(new World(GameMode.COOP), seed);
    }

    /**
     * A mirror of {@link GameLoop#stepFight()}, minus the audio, the phase machine and the scoring.
     *
     * Reproduced rather than called because GameLoop needs a Scene, a SoundBank and four
     * preferences stores to exist. The tick order below is the load-bearing part and must stay in
     * step with the real one: controllers, update, enemies, director, collisions, sweep.
     */
    private static long simulate(World world, long seed) {
        Level level = Level.values()[0];
        world.enterLevel(level);

        Random random = new Random(seed);
        SpawnDirector director =
                new SpawnDirector(random, Difficulty.NORMAL, GameMode.COOP.rules());
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, random);
        InputState input = new InputState();

        List<ShipController> controllers = new ArrayList<>();
        for (PlayerShip player : world.players()) {
            controllers.add(new ShipController(player, BOUND_KEYS, number -> null));
        }

        long rolling = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            script(input, tick);
            for (ShipController controller : controllers) {
                controller.apply(input, world, SoundPlayer.SILENT, StickTuning.none());
            }
            world.update();
            driveEnemies(world, director);
            director.update(world);
            collisions.resolve(world);
            world.sweep();
            rolling = rolling * 31 + world.checksum();
        }
        return rolling;
    }

    /**
     * A fixed input tape. Both pilots fly the same keys, which is fine -- the point is a sequence
     * that is identical between runs and lively enough to put shots in the air and walk the ships
     * into the arena walls, not a plausible pilot.
     *
     * Driven through {@link InputState#clear()} and {@code press} rather than a Scene, so no
     * toolkit starts. There is no release, so the set is rebuilt each tick.
     */
    private static void script(InputState input, int tick) {
        input.clear();
        int beat = tick % 240;
        if (beat < 60) {
            input.press(KeyCode.A);
        } else if (beat < 120) {
            input.press(KeyCode.D);
        } else if (beat < 180) {
            input.press(KeyCode.W);
            input.press(KeyCode.A);
        } else {
            input.press(KeyCode.S);
            input.press(KeyCode.D);
        }
        // Held rather than tapped: the weapon has its own cooldown, so this is already a rhythm.
        if (tick % 5 != 0) {
            input.press(KeyCode.SPACE);
        }
    }

    /** A mirror of {@link GameLoop#driveEnemies()}, indexed for the same reason it is. */
    private static void driveEnemies(World world, SpawnDirector director) {
        int difficultyCooldown = Difficulty.NORMAL.enemyFireCooldown();
        int enemyCap = Difficulty.NORMAL.maxEnemies();
        List<EnemyShip> enemies = world.enemies();
        for (int i = 0, count = enemies.size(); i < count; i++) {
            EnemyShip enemy = enemies.get(i);
            PlayerShip target = world.nearestPlayer(enemy);
            if (target == null) {
                continue;
            }
            enemy.trackAcross(target);
            if (!enemy.hasEntered()) {
                continue;
            }
            EnemyWeapons.driveWeapons(world, enemy, target, director.level(), difficultyCooldown,
                    enemyCap, SoundPlayer.SILENT);
        }
    }
}
