package com.hashimjacobs.spacecase.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.net.LevelStart;
import com.hashimjacobs.spacecase.net.Replay;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recorded run replays to its own score. The recording side here is a scripted fight through the
 * same tick order GameLoop and Replayer share; what is pinned is that the bytes round-trip and that
 * replaying them lands on the same numbers, which is what the leaderboard verifier relies on.
 */
class ReplayTest {

    private static final long SEED = 777;

    @Test
    void aRecordedFightReplaysToTheSameScore() throws IOException {
        Replay.Recorder recorder = new Replay.Recorder(new Replay.Header(SEED, GameMode.SOLO, Difficulty.NORMAL, false, false, 1));
        int expected = play(recorder, 2400);
        byte[] packed = recorder.finish();
        assertTrue(packed.length < 60_000, "two thousand ticks should pack small: " + packed.length);

        Replay.Parsed parsed = Replay.read(packed);
        assertEquals(1, parsed.segments().size());
        assertEquals(recorder.ticks(), parsed.segments().get(0).ticks().size());
        Replayer.Outcome outcome = Replayer.run(parsed);
        assertEquals(List.of(expected), outcome.scores(), "the replay must reach the recorded score");
        assertTrue(expected > 0, "a scripted fight that scores nothing would agree trivially");
    }

    @Test
    void aResumedRunRefusesToReplay() throws IOException {
        Replay.Recorder recorder = new Replay.Recorder(new Replay.Header(SEED, GameMode.SOLO, Difficulty.NORMAL, false, true, 1));
        recorder.level(new LevelStart(SEED, 0, Difficulty.NORMAL, SaveSlot.forReplay(GameMode.SOLO, Level.values()[0]), List.of("")));
        Replay.Parsed parsed = Replay.read(recorder.finish());
        assertThrows(IllegalArgumentException.class, () -> Replayer.run(parsed));
    }

    /** Fights level one with a bot that strafes and fires, recording every intent, and returns the score. */
    private static int play(Replay.Recorder recorder, int ticks) {
        Random random = new Random(SEED);
        World world = new World(GameMode.SOLO);
        SpawnDirector director = new SpawnDirector(random, Difficulty.NORMAL, GameMode.SOLO.rules());
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, random);
        world.enterLevel(director.level());
        PlayerShip me = world.players().get(0);
        me.returnToSpawn();
        List<String> loadouts = List.of(me.loadout().encode());
        world.fitLoadouts(loadouts);
        recorder.level(new LevelStart(SEED, world.tick(), Difficulty.NORMAL,
                SaveSlot.forReplay(GameMode.SOLO, director.level()), loadouts));
        ShipController controller = new ShipController(me, new PlayerControls(
                java.util.Set.of(javafx.scene.input.KeyCode.W), java.util.Set.of(javafx.scene.input.KeyCode.S),
                java.util.Set.of(javafx.scene.input.KeyCode.A), java.util.Set.of(javafx.scene.input.KeyCode.D),
                java.util.Set.of(javafx.scene.input.KeyCode.SPACE)), number -> null);
        Random bot = new Random(99);
        for (int tick = 0; tick < ticks; tick++) {
            Intent intent = new Intent(bot.nextInt(3) - 1.0, bot.nextDouble() < 0.1 ? -1 : 0, tick % 7 != 0);
            List<Intent> intents = new ArrayList<>();
            intents.add(intent);
            recorder.tick(intents);
            boolean frozen = world.frozen();
            if (!frozen) {
                controller.apply(intent, world, SoundPlayer.SILENT);
            }
            world.update();
            if (!frozen) {
                Replayer.driveEnemies(world, director, Difficulty.NORMAL, SoundPlayer.SILENT);
                director.update(world);
                collisions.resolve(world);
            }
            world.sweep();
            if (director.levelCleared() || me.isOut()) {
                break;
            }
        }
        return me.score();
    }
}
