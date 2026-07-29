package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The injected Random is what makes any of this assertable. */
class SpawnDirectorTest {

    private static final int FRAMES = 4000;

    @Test
    void theSameSeedProducesTheSameRun() {
        int first = countAsteroids(1234, Difficulty.NORMAL);
        int second = countAsteroids(1234, Difficulty.NORMAL);
        assertEquals(first, second);
    }

    @Test
    void harderDifficultySpawnsMoreAsteroids() {
        int easy = countAsteroids(99, Difficulty.EASY);
        int hard = countAsteroids(99, Difficulty.HARD);
        assertTrue(hard > easy, "hard spawned " + hard + " but easy spawned " + easy);
    }

    @Test
    void battleModeSpawnsAsteroidsAndPickupsButNoEnemies() {
        World world = new World(GameMode.BATTLE);
        SpawnDirector director = new SpawnDirector(
                new Random(7), Difficulty.NORMAL, GameMode.BATTLE.rules());

        for (int i = 0; i < FRAMES; i++) {
            director.update(world);
        }

        assertTrue(world.enemies().isEmpty(), "battle mode must not spawn AI ships");
        assertTrue(world.asteroids().size() > 0, "battle mode keeps asteroid hazards");
    }

    @Test
    void soloModeEventuallyFieldsEnemiesWithinTheDifficultyCap() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(3), Difficulty.NORMAL, GameMode.SOLO.rules());

        for (int i = 0; i < FRAMES; i++) {
            director.update(world);
        }

        assertTrue(world.enemies().size() > 0, "solo mode should field enemies");
        assertTrue(world.enemies().size() <= Difficulty.NORMAL.maxEnemies(),
                "enemy count must respect the difficulty cap");
    }

    @Test
    void wavesAdvanceOverTime() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(11), Difficulty.NORMAL, GameMode.SOLO.rules());

        assertEquals(1, director.waveInLevel());
        for (int i = 0; i < 3100; i++) {
            director.update(world);
        }
        assertTrue(director.waveInLevel() > 1, "waves should advance as ticks accumulate");
    }

    @Test
    void theLevelBossArrivesOnceItsWavesHaveBeenFought() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());
        Level first = director.level();

        boolean sawBoss = runUntilBoss(director, world, 12000);

        assertTrue(sawBoss, "the level boss should arrive after its waves");
        assertEquals(first.boss(), world.boss().boss(), "the level's own boss should be the one that came");
    }

    @Test
    void theBossDoesNotArriveBeforeItsWavesAreFought() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());

        // One tick short of the wave count the first level asks for.
        int ticks = Level.values()[0].wavesBeforeBoss() * 1500 - 1;
        for (int i = 0; i < ticks; i++) {
            director.update(world);
        }

        assertTrue(!world.bossPresent(), "the boss must wait until the level's waves are done");
    }

    @Test
    void killingTheBossFlagsTheLevelClearedWithoutAdvancingIt() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());
        Level first = director.level();

        runUntilBoss(director, world, 12000);
        assertFalse(director.levelCleared(), "the level is not cleared while the boss lives");

        killBoss(world);
        director.update(world);

        assertTrue(director.levelCleared(), "killing the boss should raise the cleared flag");
        assertEquals(first, director.level(),
                "the director must hold position so the loop can run its debrief");
    }

    @Test
    void advancingAfterAClearMovesToTheNextLevelAndResetsItsWaves() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());
        Level first = director.level();

        runUntilBoss(director, world, 12000);
        killBoss(world);
        director.update(world);
        director.advanceLevel();

        assertEquals(first.next(), director.level(), "advancing should move to the next level");
        assertFalse(director.levelCleared(), "advancing should clear the flag");
        assertEquals(1, director.waveInLevel(), "each level starts again at its first wave");
    }

    @Test
    void wavesSurvivedKeepsCountingAcrossLevels() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());

        runUntilBoss(director, world, 12000);
        int beforeAdvance = director.wavesSurvived();
        killBoss(world);
        director.update(world);
        director.advanceLevel();

        assertTrue(beforeAdvance > 1, "the first level takes several waves");
        assertEquals(beforeAdvance, director.wavesSurvived(),
                "the run total must not reset when the per-level counter does");
    }

    @Test
    void theLevelDoesNotAdvanceWhileTheBossIsAlive() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());
        Level first = director.level();

        runUntilBoss(director, world, 12000);
        for (int i = 0; i < 5000; i++) {
            director.update(world);
        }

        assertEquals(first, director.level(), "an unfought boss must block progress indefinitely");
    }

    @Test
    void clearingEveryLevelWrapsBackToTheFirst() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());

        for (int level = 0; level < Level.values().length; level++) {
            boolean sawBoss = runUntilBoss(director, world, 20000);
            assertTrue(sawBoss, "level " + (level + 1) + " should field its boss");
            clearLevel(director, world);
        }

        assertEquals(Level.values()[0], director.level(),
                "past the last level the run should loop rather than end");
    }

    @Test
    void loopingRaisesSpawnPressure() {
        int firstPass = countEnemiesSpawnedOnLevel(0);
        int secondPass = countEnemiesSpawnedOnLevel(Level.values().length);
        assertTrue(secondPass > firstPass,
                "a second pass should spawn harder; first " + firstPass + " later " + secondPass);
    }

    /** Enemies spawned over a fixed window after clearing the given number of levels. */
    private int countEnemiesSpawnedOnLevel(int levelsToClear) {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());

        for (int i = 0; i < levelsToClear; i++) {
            runUntilBoss(director, world, 20000);
            clearLevel(director, world);
        }

        int spawned = 0;
        for (int i = 0; i < FRAMES; i++) {
            int before = world.enemies().size();
            director.update(world);
            spawned += Math.max(0, world.enemies().size() - before);
            // Keep the arena clear so the difficulty cap never masks the spawn rate.
            world.enemies().forEach(enemy -> {
                if (!enemy.isBoss()) {
                    enemy.kill();
                }
            });
            world.sweep();
        }
        return spawned;
    }

    private boolean runUntilBoss(SpawnDirector director, World world, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            director.update(world);
            if (world.bossPresent()) {
                return true;
            }
        }
        return false;
    }

    private void killBoss(World world) {
        world.boss().kill();
        world.sweep();
    }

    /** Kills the boss and takes the level turnover the game loop would otherwise run. */
    private void clearLevel(SpawnDirector director, World world) {
        killBoss(world);
        director.update(world);
        director.advanceLevel();
    }

    private int countAsteroids(long seed, Difficulty difficulty) {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(seed), difficulty, GameMode.SOLO.rules());
        int spawned = 0;
        for (int i = 0; i < FRAMES; i++) {
            int before = world.asteroids().size();
            director.update(world);
            spawned += world.asteroids().size() - before;
        }
        return spawned;
    }
}
