package com.hashimjacobs.spacecase;

/**
 * TEMPORARY, Android only: switches for finding where the Shield build's garbage comes from, and a
 * sampler that turns heap growth into megabytes a second. Deleted once the answer is known.
 */
public final class Probe {

    public static volatile boolean noDraw;
    public static volatile boolean noHud;
    public static volatile boolean noBars;
    public static volatile boolean noText;

    private static volatile String phase = "startup";
    private static long grown;
    private static long last = -1;
    private static long since = System.nanoTime();

    private Probe() {
    }

    /** Starts sampling the used heap ten times a second, counting only growth, so collections drop out. */
    public static void start() {
        Thread sampler = new Thread(() -> {
            Runtime runtime = Runtime.getRuntime();
            while (true) {
                long used = runtime.totalMemory() - runtime.freeMemory();
                synchronized (Probe.class) {
                    if (last >= 0 && used > last) {
                        grown += used - last;
                    }
                    last = used;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException stop) {
                    return;
                }
            }
        }, "probe");
        sampler.setDaemon(true);
        sampler.start();
    }

    /** Reports the phase that just ended and starts the next. */
    public static synchronized void phase(String next) {
        long now = System.nanoTime();
        double seconds = (now - since) / 1e9;
        System.err.println(String.format("[alloc] %-34s %5.1f MB/s over %4.1f s",
                phase, grown / 1048576.0 / Math.max(0.1, seconds), seconds));
        phase = next;
        grown = 0;
        since = now;
    }
}
