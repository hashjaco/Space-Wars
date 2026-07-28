package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.hashimjacobs.spacecase.mode.GameMode;

/** Best score per mode, persisted across runs. */
public final class HighScores {

    private final Preferences store;

    private HighScores(Preferences store) {
        this.store = store;
    }

    public static HighScores load() {
        Preferences store = Preferences.userNodeForPackage(HighScores.class).node("highscores");
        HighScores scores = new HighScores(store);
        return scores;
    }

    /** Package-private so tests can supply a throwaway node. */
    static HighScores load(Preferences store) {
        HighScores scores = new HighScores(store);
        return scores;
    }

    public int best(GameMode mode) {
        int best = store.getInt(mode.name(), 0);
        return best;
    }

    /** Records the score if it beats the stored best. Returns true when a new record was set. */
    public boolean submit(GameMode mode, int score) {
        int previous = best(mode);
        if (score <= previous) {
            return false;
        }
        store.putInt(mode.name(), score);
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Losing a high score is not worth interrupting play over.
        }
        return true;
    }
}
