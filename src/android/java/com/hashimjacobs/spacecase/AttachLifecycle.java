package com.hashimjacobs.spacecase;

import com.gluonhq.attach.lifecycle.LifecycleEvent;
import com.gluonhq.attach.lifecycle.LifecycleService;

/**
 * Android's pause and resume, through Gluon Attach. Compiled only by the {@code mobile} profile.
 *
 * Attach runs every listener on the JavaFX thread itself, so these are passed straight through --
 * and so nothing here may ever block that thread waiting for the other event: the resume would be
 * queued behind the wait. (Tried; the game came back frozen.)
 */
public final class AttachLifecycle implements PlatformLifecycle {

    @Override
    public void listen(Runnable onBackground, Runnable onForeground) {
        LifecycleService service = LifecycleService.create()
                .orElseThrow(() -> new IllegalStateException("no Attach lifecycle service"));
        service.addListener(LifecycleEvent.PAUSE, onBackground);
        service.addListener(LifecycleEvent.RESUME, onForeground);
    }
}
