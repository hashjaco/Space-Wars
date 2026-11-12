package com.hashimjacobs.spacecase;

/**
 * Being sent to the background and brought back, on a platform that does that to an app.
 *
 * A desktop window that loses focus is still on screen and still wanted; an Android app whose
 * activity is paused is gone from the television while its process, and its music, carry on. So
 * this does nothing on the desktop, and on Android hands the two moments to whoever asked.
 */
public interface PlatformLifecycle {

    /** Both callbacks arrive on the JavaFX thread. */
    void listen(Runnable onBackground, Runnable onForeground);

    /**
     * Starts listening. Call it once launch has settled, not during it: Attach's native setup and
     * the activity's own first resume crash each other if they overlap (see SoundBank).
     */
    static void start(Runnable onBackground, Runnable onForeground) {
        if (!OnAndroid.ACTIVE) {
            return;
        }
        try {
            // By name: compiled only into the Android build, against a library the desktop lacks.
            PlatformLifecycle android = (PlatformLifecycle) Class
                    .forName("com.hashimjacobs.spacecase.AttachLifecycle")
                    .getDeclaredConstructor().newInstance();
            android.listen(onBackground, onForeground);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            System.err.println("[lifecycle] not available, the game will not pause itself: " + unavailable);
        }
    }
}
