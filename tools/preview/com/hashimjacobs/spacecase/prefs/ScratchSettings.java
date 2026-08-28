package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.Preferences;

/**
 * Opens {@link Settings}' throwaway-node loader to {@code tools/preview}.
 *
 * {@code Settings.load(Preferences)} is package-private with the note "so tests can supply a
 * throwaway node", and a capture wants exactly that. The public {@code Settings.load()} reads the
 * machine's real store, which is the wrong thing here in a way that is easy to miss: a pilot who has
 * turned {@code reducedFlash} on would silently get footage with no screen shake and no vignette,
 * and nothing in the output would say why.
 *
 * Lives in this package for the same reason MapSmoke and GarageSmoke live in theirs -- reaching a
 * package-private member is the whole point, and Java scopes that by package name rather than by
 * source directory.
 */
public final class ScratchSettings {

    /** Defaults, on a node nothing else will ever read. Caller removes it when the run ends. */
    public static Settings on(Preferences node) {
        return Settings.load(node);
    }

    private ScratchSettings() {
    }
}
