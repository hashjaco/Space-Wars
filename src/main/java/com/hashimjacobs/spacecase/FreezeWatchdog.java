package com.hashimjacobs.spacecase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/**
 * Prints what the JavaFX application thread is stuck in, if it ever stops answering.
 *
 * A frozen window is the one failure this game cannot diagnose after the fact. Everything the loop
 * does -- simulation, drawing, audio, the gamepad poll -- runs on that one thread, and several of
 * those calls end up in native code. If any of them blocks, the window stops repainting and stops
 * responding, no exception is thrown, and nothing reaches stderr. There is nothing to read
 * afterwards but the report that it hung.
 *
 * So: a daemon thread posts an empty task to the application thread and times how long it takes to
 * come back. Past {@link #STALL_SECONDS} that is not a slow frame, it is a block, and the stack
 * trace of the thread naming the native frame is the whole answer. One recurrence identifies the
 * culprit instead of costing another session of guessing.
 *
 * ponytail: kept after the freeze it was written for was found and fixed -- sound effects moved off
 * JavaFX, see {@code asset.SoundBank}. It stays because it is the only thing that can describe the
 * next one, and because everything the loop does still ends in native code somewhere. Costs one
 * heartbeat per second on a thread that is otherwise asleep. Delete it if that ever stops being
 * worth it, not merely because nothing has frozen lately.
 */
final class FreezeWatchdog {

    /** Long enough that a slow level-load or a window drag is not reported as a hang. */
    private static final int STALL_SECONDS = 5;
    private static final long BEAT_INTERVAL_MILLIS = 1000;

    private FreezeWatchdog() {
    }

    static void install() {
        Thread watchdog = new Thread(FreezeWatchdog::watch, "freeze-watchdog");
        // Daemon, so a wedged application thread cannot also keep the JVM alive after the window
        // is gone -- the point is to report the freeze, not to outlive it.
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void watch() {
        boolean reported = false;
        // When the stall began, not when the beat that noticed it began. Timing from the recovering
        // beat reported roughly how long that one beat took, which is not the length of anything
        // anyone cares about.
        long stalledSince = 0;
        while (true) {
            long start = System.currentTimeMillis();
            CountDownLatch answered = new CountDownLatch(1);
            Platform.runLater(answered::countDown);

            boolean alive;
            try {
                alive = answered.await(STALL_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException stopped) {
                return;
            }

            if (!alive) {
                // Only the first stalled beat of an episode prints: a hung thread stays hung, and
                // repeating the same stack every five seconds buries it.
                if (!reported) {
                    stalledSince = start;
                    report();
                    reported = true;
                }
            } else {
                if (reported) {
                    long stalledFor = System.currentTimeMillis() - stalledSince;
                    System.err.println("[watchdog] application thread recovered after "
                            + stalledFor + "ms");
                    reported = false;
                }
                try {
                    Thread.sleep(BEAT_INTERVAL_MILLIS);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        }
    }

    /**
     * Dumps every thread, not just the stalled one.
     *
     * The first time this fired, the application thread came back RUNNABLE with an empty stack,
     * which says the thread is in native code but not which native code. The threads either side
     * of it -- the media event queues, the audio thread -- are what give that frame its context,
     * so the dump is no longer filtered.
     */
    /**
     * Where a dump goes when there is no terminal to print to.
     *
     * Double-clicking a jar is how most people will run this, and stderr goes nowhere in that case
     * -- so the one artefact that explains a freeze would be lost exactly when it is hardest to
     * reproduce. Written to the home directory because the jar's own directory may not be writable.
     */
    private static void alsoWriteToFile(String dump) {
        try {
            Path file = Path.of(System.getProperty("user.home"), "space-case-freeze.txt");
            Files.writeString(file, dump, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.err.println("[watchdog] this dump was also written to " + file);
        } catch (IOException | RuntimeException unwritable) {
            // A dump we could not save is not worth a second failure on the way out.
            System.err.println("[watchdog] could not save the dump: " + unwritable);
        }
    }

    private static void report() {
        // Built whole rather than printed line by line, so the same text reaches the terminal and
        // the file, and so a dump cannot come out interleaved with anything else on stderr.
        StringBuilder dump = new StringBuilder();
        dump.append(System.lineSeparator());
        dump.append("[watchdog] JavaFX application thread has not responded for ")
                .append(STALL_SECONDS)
                .append("s. The game is frozen. All threads follow.")
                .append(System.lineSeparator());
        dump.append("[watchdog] A RUNNABLE thread with no frames is inside a native call:")
                .append(" the JVM has no Java frame to report for it.")
                .append(System.lineSeparator());
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] frames = entry.getValue();
            dump.append(System.lineSeparator());
            dump.append('"').append(thread.getName()).append("\" state=").append(thread.getState())
                    .append(frames.length == 0 ? "  <no Java frames -- in native code>" : "")
                    .append(System.lineSeparator());
            for (StackTraceElement frame : frames) {
                dump.append("    at ").append(frame).append(System.lineSeparator());
            }
        }
        dump.append(System.lineSeparator());
        dump.append("[watchdog] end of dump. Please report everything above.")
                .append(System.lineSeparator());

        System.err.print(dump);
        System.err.flush();
        alsoWriteToFile(dump.toString());
    }
}
