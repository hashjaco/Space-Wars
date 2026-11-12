package com.hashimjacobs.spacecase;

import javafx.application.Platform;

import com.gluonhq.attach.lifecycle.LifecycleEvent;
import com.gluonhq.attach.lifecycle.LifecycleService;

/**
 * Android's pause and resume, through Gluon Attach. Compiled only by the {@code mobile} profile.
 *
 * <h2>Why the JavaFX thread is parked while the app is away</h2>
 *
 * Android destroys the app's drawing surface about a second after pausing it. JavaFX knows nothing
 * of that and keeps rendering anything that animates -- the title ship, a paused level's veil --
 * and a draw against the destroyed surface never returns. Measured on a Shield: the render thread
 * sat inside a GL call, the JavaFX thread waited on it, the app could not take its surface back
 * when reopened, and Android killed it for not responding.
 *
 * Nothing renders unless the JavaFX thread runs a pulse, so that thread is held here from the pause
 * until the resume. The two events arrive on Android's own thread, which is what lets one release
 * what the other parked. The game's freeze watchdog will log one stall for it; that is expected.
 */
public final class AttachLifecycle implements PlatformLifecycle {

    private final Object gate = new Object();
    /** Guarded by {@link #gate}. Set on Android's thread, waited on by the JavaFX thread. */
    private boolean away;

    @Override
    public void listen(Runnable onBackground, Runnable onForeground) {
        LifecycleService service = LifecycleService.create()
                .orElseThrow(() -> new IllegalStateException("no Attach lifecycle service"));
        service.addListener(LifecycleEvent.PAUSE, () -> {
            synchronized (gate) {
                away = true;
            }
            Platform.runLater(() -> {
                onBackground.run();
                synchronized (gate) {
                    // A flag and a loop, not a latch made here: a resume that beats this runnable
                    // to the thread has already cleared the flag, and nothing parks at all.
                    while (away) {
                        try {
                            gate.wait();
                        } catch (InterruptedException closing) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                onForeground.run();
            });
        });
        service.addListener(LifecycleEvent.RESUME, () -> {
            synchronized (gate) {
                away = false;
                gate.notifyAll();
            }
        });
    }
}
