package com.hashimjacobs.spacecase.net;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.http.HttpClient;

/**
 * The two things {@code HttpClient} learned in JDK 21, called so that the source still compiles at 17.
 *
 * The desktop game runs on a current JDK, where both of these do exactly what calling
 * {@code close()} and {@code shutdownNow()} directly did. The Android build has to compile at 17 --
 * the only Gluon compiler that can target Android is its java17 one -- and there neither method
 * exists, so both become no-ops and the client is left to the garbage collector, which is what a
 * JDK 17 program has always done with one.
 */
public final class Http {

    /** {@code HttpClient.shutdownNow()}, or null on a runtime older than 21. */
    private static final MethodHandle SHUTDOWN_NOW = findShutdownNow();

    private Http() {
    }

    /** What try-with-resources on the client did: waits for anything in flight, then releases it. */
    public static void close(HttpClient http) {
        // Through Object, because at release 17 an HttpClient is not known to be closeable at all.
        if ((Object) http instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception impossible) {
                // HttpClient.close() declares nothing; AutoCloseable's signature is all this answers.
            }
        }
    }

    /** Drops the client's connections without waiting on them. See {@code RelayClient.close}. */
    public static void shutdownNow(HttpClient http) {
        if (SHUTDOWN_NOW == null) {
            return;
        }
        try {
            SHUTDOWN_NOW.invoke(http);
        } catch (RuntimeException | Error thrown) {
            throw thrown;
        } catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static MethodHandle findShutdownNow() {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(HttpClient.class, "shutdownNow", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException olderRuntime) {
            return null;
        }
    }
}
