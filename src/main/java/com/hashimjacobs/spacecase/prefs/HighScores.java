package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

/** Best score per mode and per level, persisted across runs. */
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
        return submit(mode.name(), score);
    }

    /**
     * Best single-level performance, which is the target a replay is chasing.
     *
     * Separate from the per-mode run total because they measure different things: a run score
     * accumulates across however many levels you survived, so it cannot be compared between a
     * fresh replay of level two and a deep run that happened to pass through it.
     */
    public int best(GameMode mode, Level level) {
        return store.getInt(key(mode, level), 0);
    }

    public boolean submit(GameMode mode, Level level, int score) {
        return submit(key(mode, level), score);
    }

    private static String key(GameMode mode, Level level) {
        return mode.name() + "/" + level.name();
    }

    private boolean submit(String key, int score) {
        int previous = store.getInt(key, 0);
        if (score <= previous) {
            return false;
        }
        store.putInt(key, score);
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Losing a high score is not worth interrupting play over.
        }
        return true;
    }
}
