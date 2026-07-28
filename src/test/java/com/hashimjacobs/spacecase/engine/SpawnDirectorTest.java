package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.Difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(1, director.wave());
        for (int i = 0; i < 3100; i++) {
            director.update(world);
        }
        assertTrue(director.wave() > 1, "waves should advance as ticks accumulate");
    }

    @Test
    void aBossArrivesOnTheFourthWave() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());

        boolean sawBoss = false;
        // Four waves at 1500 ticks each, with headroom.
        for (int i = 0; i < 7000 && !sawBoss; i++) {
            director.update(world);
            sawBoss = world.bossPresent();
        }
        assertTrue(sawBoss, "a boss should appear by the fourth wave");
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
