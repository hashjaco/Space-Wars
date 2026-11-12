package com.hashimjacobs.spacecase;

import javafx.application.Platform;

import com.gluonhq.attach.lifecycle.LifecycleEvent;
import com.gluonhq.attach.lifecycle.LifecycleService;

/** Android's pause and resume, through Gluon Attach. Compiled only by the {@code mobile} profile. */
public final class AttachLifecycle implements PlatformLifecycle {

    @Override
    public void listen(Runnable onBackground, Runnable onForeground) {
        LifecycleService service = LifecycleService.create()
                .orElseThrow(() -> new IllegalStateException("no Attach lifecycle service"));
        service.addListener(LifecycleEvent.PAUSE, () -> Platform.runLater(onBackground));
        service.addListener(LifecycleEvent.RESUME, () -> Platform.runLater(onForeground));
    }
}
