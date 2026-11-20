package com.hashimjacobs.spacecase;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.gluonhq.attach.storage.StorageService;

/**
 * The app's private files folder, where an update's APK is downloaded. Compiled only by the
 * {@code mobile} profile; {@code update.Updates} finds it by name.
 *
 * Attach answers the activity's {@code getFilesDir()}, the same folder {@code TvActivity} watches
 * and {@code ApkProvider} serves the APK from.
 */
public final class AttachUpdateFolder implements Supplier<Path> {

    @Override
    public Path get() {
        return StorageService.create()
                .flatMap(StorageService::getPrivateStorage)
                .orElseThrow(() -> new IllegalStateException("no Attach private storage"))
                .toPath();
    }
}
