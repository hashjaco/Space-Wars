package com.hashimjacobs.spacecase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * Drives the audio stack at the game's own trigger rates and reports what it cost.
 *
 * Three freezes were traced to JavaFX building a native media player per sound effect and tearing
 * it down again, with the soundtrack's own pulse contending for the same native lock on the thread
 * that draws the game. The lesson recorded from that hunt was that a probe which merely fails to
 * reproduce an intermittent native race proves nothing -- twice a fix was called good on exactly
 * that evidence and twice it was wrong. So this measures the mechanism directly rather than waiting
 * to see whether anything hangs.
 *
 * <h2>What it reports</h2>
 *
 * <ul>
 *   <li><b>Peak native media players</b>, counted as live threads named
 *       {@code JFXMedia Player EventQueueThread}. One of those exists per native player, so the
 *       count is the churn. Effects moved onto {@code javax.sound.sampled} should take this to
 *       whatever the music alone needs and hold it there.</li>
 *   <li><b>Worst application-thread heartbeat</b>, as a {@code Platform.runLater} round trip. The
 *       freeze is that thread stuck in native code, so this is the symptom itself, sampled.</li>
 *   <li><b>The effect tally</b> from {@link SoundBank}: requested, started, dropped.</li>
 * </ul>
 *
 * <h2>Why it lives here and not in tools/</h2>
 *
 * It needs the game's own classes and a real audio device, so it can be neither a standalone
 * single-file program like {@code tools/GenerateAssets.java} nor part of a suite that deliberately
 * never starts the toolkit. Run it by hand:
 *
 * <pre>./mvnw -q javafx:run -Djavafx.mainClass=com.hashimjacobs.spacecase.AudioSoak</pre>
 *
 * Music plays throughout, deliberately. Without it the soundtrack's native backend is never loaded
 * and there is nothing for the effects to contend with, which would make a clean run meaningless.
 */
public final class AudioSoak extends Application {

    /** Named by JavaFX; one lives for as long as its native media player does. */
    private static final String MEDIA_THREAD = "JFXMedia Player EventQueueThread";

    private static final long SAMPLE_INTERVAL_MILLIS = 50;

    /** Ticks per second the game simulates at, so a cadence in ticks reads the same as in game. */
    private static final int TICKS_PER_SECOND = 60;

    private final List<String> rows = new ArrayList<>();
    private volatile int peakMediaThreads;
    private volatile double worstHeartbeatMs;
    private volatile boolean running = true;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Settings settings = Settings.load();
        SoundBank sounds = new SoundBank(settings);
        // The soundtrack has to be playing: its pulse on the application thread is one half of the
        // contention being measured.
        sounds.playMusic(MusicCue.BOSS);

        Thread sampler = new Thread(this::sample, "soak-sampler");
        sampler.setDaemon(true);
        sampler.start();

        Thread driver = new Thread(() -> {
            try {
                drive(sounds);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            running = false;
            report(sounds);
            sounds.shutdown();
            Platform.exit();
        }, "soak-driver");
        driver.setDaemon(true);
        driver.start();
    }

    /**
     * The profiles that matter, in the order a player meets them.
     *
     * Boss two is first because that is where the first freeze happened, and its shape is unusual:
     * its middle phase fires no gun at all while it fills the arena with escorts, so the pool
     * drains and is then slammed by a fivefold step change with a backlog of three-second death
     * sounds behind it.
     */
    private void drive(SoundBank sounds) throws InterruptedException {
        phase(sounds, "boss 2, spread phase", 20, 2.5, 0);
        phase(sounds, "boss 2, spawner phase (gun silent)", 20, 0, 0);
        phase(sounds, "boss 2, aimed burst + escort deaths", 30, 5.0, 1.2);
        phase(sounds, "spiral, the fastest pattern", 20, 6.0, 0.4);
        phase(sounds, "cooldown floor, worst case", 15, 15.0, 0.4);
        curtains(sounds, 15);
    }

    /**
     * One stretch of fighting.
     *
     * Player fire runs throughout at the maxed rate for two seats, because a boss fight is exactly
     * when both players hold the trigger down.
     *
     * @param bossVolleysPerSecond flagship gun rate, or zero while it is spawning
     * @param shipDeathsPerSecond  escorts dying, each a three-second sample
     */
    private void phase(SoundBank sounds, String label, int seconds, double bossVolleysPerSecond,
                       double shipDeathsPerSecond) throws InterruptedException {
        long start = System.nanoTime();
        int peakBefore = peakMediaThreads;
        double heartbeatBefore = worstHeartbeatMs;

        // Two seats, fire cooldown 7 ticks fully upgraded.
        double playerShotsPerSecond = 2.0 * TICKS_PER_SECOND / 7.0;
        Emitter[] emitters = {
                new Emitter(SoundFx.LASER, playerShotsPerSecond),
                new Emitter(SoundFx.BOSS_GUN, bossVolleysPerSecond),
                new Emitter(SoundFx.SHIP_EXPLOSION, shipDeathsPerSecond),
                new Emitter(SoundFx.EXPLOSION, 2.0),
                new Emitter(SoundFx.BOSS_ROCKET, 0.4),
        };

        long until = start + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < until) {
            long now = System.nanoTime();
            for (Emitter emitter : emitters) {
                if (emitter.due(now)) {
                    sounds.play(emitter.effect);
                }
            }
            Thread.sleep(1000 / TICKS_PER_SECOND);
        }
        rows.add(row(label, seconds, peakBefore, heartbeatBefore));
    }

    /**
     * Flying into a ring curtain: nine bullets connect on one tick, so nine sounds are asked for at
     * once. The sharpest burst in the game, and it happens at the point of impact rather than at
     * the muzzle, which is why it is not visible in any fire-rate number.
     */
    private void curtains(SoundBank sounds, int seconds) throws InterruptedException {
        long start = System.nanoTime();
        int peakBefore = peakMediaThreads;
        double heartbeatBefore = worstHeartbeatMs;

        long until = start + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < until) {
            for (int bullet = 0; bullet < 9; bullet++) {
                sounds.play(SoundFx.COLLISION);
            }
            Thread.sleep(600);
        }
        rows.add(row("ring curtain, nine hits a tick", seconds, peakBefore, heartbeatBefore));
    }

    private String row(String label, int seconds, int peakBefore, double heartbeatBefore) {
        return String.format("  %-38s %2ds   peak players %2d   worst beat %6.1f ms",
                label, seconds, peakMediaThreads - peakBefore,
                Math.max(0, worstHeartbeatMs - heartbeatBefore));
    }

    /** Samples the two numbers that matter, off the application thread so a stall is visible. */
    private void sample() {
        while (running) {
            int live = 0;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().startsWith(MEDIA_THREAD)) {
                    live++;
                }
            }
            if (live > peakMediaThreads) {
                peakMediaThreads = live;
            }

            long asked = System.nanoTime();
            CountDownLatch answered = new CountDownLatch(1);
            Platform.runLater(answered::countDown);
            try {
                if (!answered.await(10, TimeUnit.SECONDS)) {
                    System.err.println("[soak] application thread stalled past 10s -- it is frozen");
                    return;
                }
                double beat = (System.nanoTime() - asked) / 1_000_000.0;
                if (beat > worstHeartbeatMs) {
                    worstHeartbeatMs = beat;
                }
                Thread.sleep(SAMPLE_INTERVAL_MILLIS);
            } catch (InterruptedException stopped) {
                return;
            }
        }
    }

    private void report(SoundBank sounds) {
        System.out.println();
        System.out.println("=== audio soak ===");
        System.out.println("Per phase, the rise over where the run already stood:");
        rows.forEach(System.out::println);
        System.out.println();
        System.out.println("  peak native media players : " + peakMediaThreads);
        System.out.printf("  worst heartbeat           : %.1f ms%n", worstHeartbeatMs);
        System.out.println("  effects                   : " + sounds.effectTally());
        System.out.println();
    }

    /** Fires an effect at a fixed rate, from a rate rather than a countdown so it cannot drift. */
    private static final class Emitter {
        private final SoundFx effect;
        private final long intervalNanos;
        private long nextAt;

        Emitter(SoundFx effect, double perSecond) {
            this.effect = effect;
            this.intervalNanos = perSecond <= 0 ? Long.MAX_VALUE
                    : (long) (1_000_000_000L / perSecond);
            this.nextAt = System.nanoTime();
        }

        boolean due(long now) {
            if (intervalNanos == Long.MAX_VALUE || now < nextAt) {
                return false;
            }
            nextAt = now + intervalNanos;
            return true;
        }
    }
}
