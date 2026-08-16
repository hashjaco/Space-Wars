package com.hashimjacobs.spacecase;

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
 * ponytail: diagnostic, not a fix -- it makes the next freeze self-reporting rather than preventing
 * it. Delete it once the offending call is known and dealt with. Costs one heartbeat per second on
 * a thread that is otherwise asleep.
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
                    report();
                    reported = true;
                }
            } else {
                if (reported) {
                    long stalledFor = System.currentTimeMillis() - start;
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
    private static void report() {
        System.err.println();
        System.err.println("[watchdog] JavaFX application thread has not responded for "
                + STALL_SECONDS + "s. The game is frozen. All threads follow.");
        System.err.println("[watchdog] A RUNNABLE thread with no frames is inside a native call:"
                + " the JVM has no Java frame to report for it.");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] frames = entry.getValue();
            System.err.println();
            System.err.println("\"" + thread.getName() + "\" state=" + thread.getState()
                    + (frames.length == 0 ? "  <no Java frames -- in native code>" : ""));
            for (StackTraceElement frame : frames) {
                System.err.println("    at " + frame);
            }
        }
        System.err.println();
        System.err.println("[watchdog] end of dump. Please report everything above.");
        System.err.flush();
    }
}
