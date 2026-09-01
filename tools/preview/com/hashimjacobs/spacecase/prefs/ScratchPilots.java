package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.Preferences;

/**
 * Opens {@link Pilots}'s throwaway-node loader to {@code tools/preview}, as {@link ScratchAccount}
 * does for the sync code and for the same reason.
 *
 * {@code Pilots.load()} reads and writes this machine's real seat names and career totals, so a
 * capture run against it would rename whoever is actually flying.
 */
public final class ScratchPilots {

    /** Two empty seats on a node nothing else will ever read. Caller removes it when done. */
    public static Pilots on(Preferences node) {
        return Pilots.load(node);
    }

    private ScratchPilots() {
    }
}
