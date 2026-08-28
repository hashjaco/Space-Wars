package com.hashimjacobs.spacecase.engine;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.ScratchSettings;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Films a fight: steps the real simulation one tick at a time, draws it with the real renderer, and
 * writes each frame to stdout as raw BGRA for ffmpeg to encode.
 *
 * This exists because there was no way to see the game move without playing it. The four harnesses
 * next to this one snapshot a screen that holds still; a trailer needs the part that does not.
 *
 * <h2>Why it rebuilds the fight instead of driving GameLoop</h2>
 *
 * GameLoop looks like the obvious thing to reach for, and is not. Its {@code frame}, {@code step}
 * and {@code stepFight} are all private, so the only way in is {@code start()} and an AnimationTimer
 * running at display refresh; it keeps its World to itself, so a bot could never see the field; it
 * takes a concrete SoundBank that it dereferences in its own constructor; and it builds Pilots from
 * the machine's real career store, which {@code scoreLevel} then writes back to -- filming would
 * edit the pilot's record. Past that, clearing a level parks the loop in a debrief that only a Scene
 * with real key events can dismiss, so a long capture would hang the first time the bot won.
 *
 * So this mirrors {@link GameLoop#stepFight()} the way {@code DeterminismTest.simulate} already
 * does, and hangs a {@link Renderer} off it. Nothing under {@code src/main} changes.
 *
 * <h2>The tick order is load-bearing</h2>
 *
 * Controllers, update, enemies, director, collisions, sweep. This is now the third copy -- the real
 * one is {@link GameLoop#stepFight()}, the second is in {@code DeterminismTest}. Change one and
 * change all three, or the footage stops being the game.
 *
 * <h2>Raw frames rather than PNGs</h2>
 *
 * A ninety-second trailer is 5400 frames. Encoding those as PNG on the JavaFX thread costs about
 * nine minutes of pure deflate and lands 1.3 GB on disk, all of it thrown away after ffmpeg reads
 * it. Writing raw BGRA to stdout hands the encode to ffmpeg, which is going to run anyway, and
 * keeps this program free of any image format at all. One bulk {@code getPixels} replaces the
 * per-pixel readback loop the still harnesses use, which is fine once and absurd 5400 times.
 *
 * ponytail: no threading. Snapshot readback does not pipeline, so the wall clock is bounded by the
 * GPU either way; a worker thread would buy back only the write() and cost a frame queue.
 *
 * <pre>
 * mvn -q compile
 * mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * CP="target/classes:$(cat /tmp/cp.txt)"
 * javac -cp "$CP" -d /tmp/preview \
 *     tools/preview/com/hashimjacobs/spacecase/prefs/ScratchSettings.java \
 *     tools/preview/com/hashimjacobs/spacecase/engine/CaptureHarness.java
 * java -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.engine.CaptureHarness 50 900 7 \
 *   | ffmpeg -f rawvideo -pixel_format bgra -video_size 996x864 -framerate 60 -i - \
 *            -c:v libx264 -pix_fmt yuv420p -crf 18 /tmp/level50.mp4
 * </pre>
 */
public final class CaptureHarness {

    private static final int WIDTH = (int) GameConfig.WIDTH;
    private static final int HEIGHT = (int) GameConfig.HEIGHT;

    /**
     * Bindings the bot never uses.
     *
     * ShipController wants a PlayerControls to exist, but the capture drives it through
     * {@code apply(Intent, ...)}, which never reads one. Built here rather than from Settings so no
     * pilot's rebound keys can change what gets filmed.
     */
    private static final PlayerControls UNUSED_KEYS = new PlayerControls(
            Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A), Set.of(KeyCode.D),
            Set.of(KeyCode.SPACE));

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: CaptureHarness <level 1-50> <ticks> [seed] [difficulty]"
                    + " [boss]");
            System.err.println("       writes raw BGRA " + WIDTH + "x" + HEIGHT + " to stdout");
            System.exit(2);
        }
        int levelNumber = Integer.parseInt(args[0]);
        int ticks = Integer.parseInt(args[1]);
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        Difficulty difficulty = args.length > 3
                ? Difficulty.valueOf(args[3].toUpperCase())
                : Difficulty.NORMAL;
        boolean straightToBoss = args.length > 4 && args[4].equalsIgnoreCase("boss");

        Level level = Level.values()[levelNumber - 1];

        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                film(level, ticks, seed, difficulty, straightToBoss);
            } catch (Exception e) {
                failure[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static void film(Level level, int ticks, long seed, Difficulty difficulty,
                             boolean straightToBoss) throws Exception {
        Assets.load();

        // A node nothing else reads, so the capture runs on defaults rather than on whatever this
        // machine happens to have set. Removed at the end -- see MapSmoke, which does the same.
        Preferences node =
                Preferences.userRoot().node("space-case-capture-" + System.nanoTime());
        Settings settings = ScratchSettings.on(node);

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        Renderer renderer = new Renderer(canvas.getGraphicsContext2D(), settings);
        StackPane root = new StackPane(canvas);
        // A Scene, but deliberately no Stage: that is what lets this run without a window.
        new Scene(root, WIDTH, HEIGHT, Tokens.SPACE);
        root.applyCss();
        root.layout();

        World world = new World(GameMode.SOLO, List.of("ACE", "NOVA"));
        // Sets the orientation and the terrain as well as the art, so side-view levels film right.
        world.enterLevel(level);

        Random random = new Random(seed);
        SpawnDirector director = new SpawnDirector(random, difficulty, GameMode.SOLO.rules(),
                level, 1, 1);
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, random);
        if (straightToBoss) {
            skipToBoss(director, level);
        }

        List<ShipController> controllers = new ArrayList<>();
        for (PlayerShip player : world.players()) {
            controllers.add(new ShipController(player, UNUSED_KEYS, number -> null));
        }
        TrailerPilot pilot = new TrailerPilot(world);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Tokens.SPACE);
        WritableImage shot = new WritableImage(WIDTH, HEIGHT);
        byte[] frame = new byte[WIDTH * HEIGHT * 4];
        WritablePixelFormat<java.nio.ByteBuffer> bgra = PixelFormat.getByteBgraInstance();

        OutputStream out = new BufferedOutputStream(System.out, frame.length);
        long startedAt = System.currentTimeMillis();
        int written = 0;
        // Reported so a seed sweep can select for runs that actually reach the flagship. A capture
        // that dies in wave two is worth nothing to a trailer however long it ran.
        int bossAtTick = -1;
        for (int tick = 0; tick < ticks; tick++) {
            for (ShipController controller : controllers) {
                controller.apply(pilot.intentFor(controller.ship().playerNumber(), tick),
                        world, SoundPlayer.SILENT);
            }
            world.update();
            driveEnemies(world, director, difficulty);
            director.update(world);
            collisions.resolve(world);
            world.sweep();

            renderer.draw(world, director);
            // Reusing one WritableImage: 5400 allocations of 3.4 MB is the easiest waste to avoid.
            root.snapshot(params, shot);
            shot.getPixelReader().getPixels(0, 0, WIDTH, HEIGHT, bgra, frame, 0, WIDTH * 4);
            out.write(frame);
            written++;
            if (bossAtTick < 0 && world.bossPresent()) {
                bossAtTick = tick;
            }

            if (allOut(world)) {
                // Say so rather than filming an empty sky for the remaining minute.
                System.err.println("all pilots out at tick " + tick + "; stopping early");
                break;
            }
            if (tick % 300 == 0 && tick > 0) {
                System.err.printf("tick %d/%d  %.1f fps%n", tick, ticks,
                        tick * 1000.0 / (System.currentTimeMillis() - startedAt));
            }
        }
        out.flush();
        node.removeNode();
        System.err.printf("%d frames, %.1fs of footage, %.1fs wall, boss %s%n",
                written, written / 60.0, (System.currentTimeMillis() - startedAt) / 1000.0,
                bossAtTick < 0 ? "never" : "at tick " + bossAtTick);
    }

    /**
     * Brings the flagship on at once, by telling the director the level's waves are already flown.
     *
     * The alternative is to fly them, and the pilot cannot: past the first galaxy it dies somewhere
     * in wave four with the flagship still unspawned, so the fights the trailer exists to show
     * would be the only ones never filmed.
     *
     * Reflection because {@code wavesIntoLevel} is private and there is no seam for this -- which is
     * correct for the game, where nothing should be able to skip a level's waves. Confined to a
     * throwaway capture tool rather than answered with a setter on shipped code, and loud if the
     * field is ever renamed, which is the trade this is worth making.
     */
    private static void skipToBoss(SpawnDirector director, Level level) {
        try {
            java.lang.reflect.Field waves =
                    SpawnDirector.class.getDeclaredField("wavesIntoLevel");
            waves.setAccessible(true);
            waves.setInt(director, level.wavesBeforeBoss());
            System.err.println("skipping to the flagship: " + level.boss().label());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "SpawnDirector.wavesIntoLevel is gone -- CaptureHarness needs updating", e);
        }
    }

    private static boolean allOut(World world) {
        for (PlayerShip player : world.players()) {
            if (!player.isOut()) {
                return false;
            }
        }
        return true;
    }

    /** A mirror of {@link GameLoop}'s enemy drive, indexed for the same reason it is. */
    private static void driveEnemies(World world, SpawnDirector director, Difficulty difficulty) {
        int cooldown = difficulty.enemyFireCooldown();
        int cap = difficulty.maxEnemies();
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
            EnemyWeapons.driveWeapons(world, enemy, target, director.level(), cooldown, cap,
                    SoundPlayer.SILENT);
        }
    }
}
