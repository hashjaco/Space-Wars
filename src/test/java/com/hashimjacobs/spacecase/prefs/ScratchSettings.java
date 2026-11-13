package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Default settings for a test in another package, read from a node that is gone again at once. */
public final class ScratchSettings {

    private ScratchSettings() {
    }

    public static Settings fresh() throws BackingStoreException {
        Preferences scratch = Preferences.userRoot().node("space-case-test-" + System.nanoTime());
        Settings settings = Settings.load(scratch);
        scratch.removeNode();
        return settings;
    }
}
