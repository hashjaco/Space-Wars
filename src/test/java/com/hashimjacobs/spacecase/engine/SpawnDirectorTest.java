package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.entity.WaveShip;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.mode.Waves;
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

    /**
     * The difficulty cap governs the filler, not the fight the level authored.
     *
     * It used to govern everything, because everything was filler. Now a level fields designed
     * waves and the trickle arrives on top of them, and the cap has to be read as "how much noise
     * over the top" rather than "how big a fight may be" -- otherwise Easy, whose cap is four,
     * could not field a six-ship wave at all, and the levels would quietly play differently from
     * the way they were written.
     *
     * So the bound here is the biggest wave the level authors plus the cap: enough headroom for the
     * fight and its filler, and still tight enough to catch a trickle that has stopped counting.
     */
    @Test
    void theDifficultyCapGovernsTheFillerNotTheAuthoredWave() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(3), Difficulty.NORMAL, GameMode.SOLO.rules());

        for (int i = 0; i < FRAMES; i++) {
            director.update(world);
        }

        // Every authored ship the level can field, because this loop never runs the world: nothing
        // descends, nothing is culled and nothing dies, so each wave times out with all its ships
        // still standing and they pile up. In a real fight they leave through the bottom.
        int authored = 0;
        for (List<WaveShip> wave : Waves.forLevel(Level.values()[0])) {
            for (int group = 0; group < Waves.GROUPS_PER_WAVE; group++) {
                authored += Waves.group(wave, group).size();
            }
        }

        assertTrue(world.enemies().size() > 0, "solo mode should field enemies");
        assertTrue(world.enemies().size() <= authored + Difficulty.NORMAL.maxEnemies(),
                "the filler has stopped respecting the difficulty cap");
    }

    /** Easy's cap is four; the level still gets to field the six-ship wave somebody wrote for it. */
    @Test
    void anAuthoredWaveArrivesWholeEvenWhenTheCapIsSmallerThanItIs() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(5), Difficulty.EASY, GameMode.SOLO.rules());
        int authored = Waves.group(Waves.forLevel(Level.values()[0]).get(0), 0).size();
        assertTrue(authored > Difficulty.EASY.maxEnemies(), "this level no longer proves the point");

        director.update(world);

        assertEquals(authored, world.enemies().size(),
                "the whole wave should arrive, cap or no cap");
    }

    /**
     * A wave arrives as a group. This is the whole feature: before it, a "wave" was a clock and
     * enemies trickled in one at a time wherever the generator put them.
     */
    @Test
    void aWaveArrivesAsAGroupRatherThanAsATrickle() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(17), Difficulty.NORMAL, GameMode.SOLO.rules());

        director.update(world);

        assertEquals(Waves.group(Waves.forLevel(Level.values()[0]).get(0), 0).size(),
                world.enemies().size(),
                "the wave's first group should be on the field after one tick");
    }

    /**
     * Killing a group is what sends the next one, and killing the last of them ends the wave.
     *
     * Asserts it happens far inside the timeout, so a pass cannot be the clock quietly doing the
     * work -- which is exactly how this feature would fail without anybody noticing.
     *
     * Three clears rather than one, because a wave is three groups now. The mid-loop assertion is
     * the half worth having: without it, a build that launched all three groups at once, or that
     * ended the wave on the first clear, would still reach wave two and pass.
     */
    @Test
    void killingAGroupStartsTheNextOneEarlyAndTheThirdEndsTheWave() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(19), Difficulty.NORMAL, GameMode.SOLO.rules());

        for (int group = 0; group < Waves.GROUPS_PER_WAVE; group++) {
            // One tick to put the group up, and one after the kills for the director to notice.
            director.update(world);
            assertEquals(group + 1, director.groupInWave(), "each group waits for the last one");
            assertEquals(1, director.waveInLevel(),
                    "the wave should still be its first until every group of it is dead");
            for (EnemyShip enemy : world.enemies()) {
                enemy.kill();
            }
            world.sweep();
            director.update(world);
        }

        assertEquals(2, director.waveInLevel(), "the next wave should already be running");
        assertEquals(1, director.groupInWave(), "which opens on its own first group");
    }

    /**
     * A survivor parked in a corner must not be able to hold a level open forever.
     *
     * The timeout is per group, so a wave nobody touches costs three of them.
     */
    @Test
    void aCampedWaveTurnsOverAnyway() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(23), Difficulty.NORMAL, GameMode.SOLO.rules());

        int waveTimeout = SpawnDirector.WAVE_TIMEOUT_TICKS * Waves.GROUPS_PER_WAVE;
        for (int i = 0; i < waveTimeout + 1; i++) {
            director.update(world);
        }

        assertTrue(director.waveInLevel() > 1, "the wave should have timed out");
    }

    /**
     * The filler cannot hold a group open.
     *
     * A group is cleared when the ships <em>it</em> ordered are gone, not when the arena is empty.
     * Getting that backwards would be invisible in play and fatal to the feature: the trickle never
     * stops, so an arena-empty rule would mean every group ran to its timeout and nothing else.
     */
    @Test
    void theFillerCannotStopAGroupFromClearing() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(29), Difficulty.HARD, GameMode.SOLO.rules());

        director.update(world);
        List<EnemyShip> wave = new ArrayList<>(world.enemies());
        // Let the trickle put something else on the field alongside the wave.
        for (int i = 0; i < 600 && world.enemies().size() <= wave.size(); i++) {
            director.update(world);
        }
        assertTrue(world.enemies().size() > wave.size(), "no filler arrived, so this proves nothing");

        for (EnemyShip enemy : wave) {
            enemy.kill();
        }
        world.sweep();
        director.update(world);

        assertFalse(world.enemies().isEmpty(), "filler should still be on the field");
        assertEquals(2, director.groupInWave(), "the group cleared when its own ships died");
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

    /**
     * Resuming starts on the saved level with the saved counters, and everything else at zero.
     *
     * The zeroes are the point: a checkpoint is only taken at a level boundary, so a resumed run
     * must open on wave one with no boss pending, exactly as advanceLevel would have left it.
     */
    @Test
    void resumingStartsOnTheSavedLevelWithACleanWaveClock() {
        SpawnDirector director = new SpawnDirector(new Random(1), Difficulty.NORMAL,
                GameMode.SOLO.rules(), Level.values()[5], 22, 3);

        assertEquals(Level.values()[5], director.level());
        assertEquals(22, director.wavesSurvived());
        assertEquals(3, director.loop());
        assertEquals(1, director.waveInLevel(), "a resumed level opens on its first wave");
        assertEquals(0, director.ticksIntoLevel());
        assertFalse(director.levelCleared());
    }

    @Test
    void aResumedRunAdvancesNormallyFromWhereItRestarted() {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(new Random(2), Difficulty.NORMAL,
                GameMode.SOLO.rules(), Level.values()[2], 9, 1);

        runUntilBoss(director, world, 20000);
        clearLevel(director, world);

        assertEquals(Level.values()[3], director.level());
    }

    /**
     * The end-to-end version of the boss-scaling knob.
     *
     * {@code DifficultyTest} checks the formula; this checks it actually reaches the ship, which is
     * the part that would silently stop working if the constructor argument were ever dropped.
     */
    @Test
    void loopingMakesFlagshipsTougher() {
        double firstPass = bossScaleAfterClearing(0);
        double secondPass = bossScaleAfterClearing(Level.values().length);

        assertTrue(secondPass > firstPass,
                "level one should be harder on the second pass; first " + firstPass
                        + " later " + secondPass);
    }

    @Test
    void flagshipsGetTougherWithinASinglePass() {
        double firstLevel = bossScaleAfterClearing(0);
        double laterLevel = bossScaleAfterClearing(3);

        assertTrue(laterLevel > firstLevel);
    }

    /** The scale carried by the flagship that arrives after clearing the given number of levels. */
    private double bossScaleAfterClearing(int levelsToClear) {
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(
                new Random(7), Difficulty.NORMAL, GameMode.SOLO.rules());

        for (int i = 0; i < levelsToClear; i++) {
            runUntilBoss(director, world, 20000);
            clearLevel(director, world);
        }
        runUntilBoss(director, world, 20000);

        return world.boss().scale();
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

    /**
     * Destroys the whole flagship, parts included.
     *
     * A multi-part boss is not dead until its heads are: the level stays open while any of them
     * lives, which is the point of them. Killing only the torso here would leave the director
     * waiting forever.
     */
    private void killBoss(World world) {
        EnemyShip flagship = world.boss();
        for (EnemyShip part : flagship.parts()) {
            part.kill();
        }
        flagship.kill();
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

    /**
     * In a tunnel, waves arrive in the part of it you can actually fly.
     *
     * Without this the level is unfair in a way that reads as a bug: enemies materialise inside the
     * rock, and the generator's own note about painted walls said as much -- "ships flying over solid
     * rock reads as a bug rather than as depth". The same applies to theirs.
     */
    @Test
    void enemiesInATunnelSpawnInsideTheOpenLane() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(Level.UNDERCITY);
        // The director has to be in the tunnel too, not just the world. It used to be left on level
        // one while the world was told to be Undercity, which passed only because both levels run
        // top-down and the trickle is clamped against whatever terrain the world hands it. The
        // moment level one fielded a wave of its own, six ships authored for open sky arrived in a
        // cave -- which is not what this test is called, and not a fair thing to assert about.
        SpawnDirector director = new SpawnDirector(
                new Random(31), Difficulty.HARD, GameMode.SOLO.rules(), Level.UNDERCITY, 1, 1);

        Terrain terrain = world.terrain();
        assertFalse(terrain.isEmpty(), "Undercity is supposed to be a cave");

        int checked = 0;
        for (int i = 0; i < FRAMES; i++) {
            director.update(world);
            for (EnemyShip enemy : world.enemies()) {
                if (enemy.isBoss()) {
                    continue;
                }
                double across = Level.UNDERCITY.orientation().across(enemy.x(), enemy.y());
                double extent = Level.UNDERCITY.orientation()
                        .acrossExtent(enemy.width(), enemy.height());
                // Read at the entry edge, which is where the clamp is applied and where a fresh
                // spawn sits. Anything already descending has been pushed clear since.
                assertTrue(across >= terrain.laneLow(0) - 1,
                        "spawned " + across + " into the near wall at " + terrain.laneLow(0));
                assertTrue(across + extent <= terrain.laneHigh(0) + 1,
                        "spawned past the far wall at " + terrain.laneHigh(0));
                checked++;
            }
            world.update();
            world.sweep();
        }
        assertTrue(checked > 0, "nothing spawned, so this test proved nothing");
    }
}
