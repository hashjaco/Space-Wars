package com.hashimjacobs.spacecase.prefs;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.hashimjacobs.spacecase.mode.GameMode;

/**
 * Where runs are kept: one automatic checkpoint, and three slots the player writes by hand.
 *
 * The checkpoint is what "Continue" resumes and what "Level Select" derives its unlocks from. It
 * is written every time a level begins, so nobody has to remember to save; the manual slots exist
 * for the things an automatic one cannot do, like keeping a co-op run while you play solo.
 *
 * Four string values in a preferences node. {@code Pilots} already stores an encoded object this
 * way and the payload here is about a hundred characters against an eight-thousand limit.
 */
public final class SaveGames {

    /** Manual slots offered on the save and load screens. */
    public static final int SLOTS = 3;

    private static final String CHECKPOINT_KEY = "checkpoint";
    private static final String SLOT_KEY_PREFIX = "slot";

    private final Preferences store;

    private SaveGames(Preferences store) {
        this.store = store;
    }

    public static SaveGames load() {
        Preferences store = Preferences.userNodeForPackage(SaveGames.class).node("saves");
        return new SaveGames(store);
    }

    /** Package-private so tests can supply a throwaway node. */
    static SaveGames load(Preferences store) {
        return new SaveGames(store);
    }

    public Optional<SaveSlot> checkpoint() {
        return SaveSlot.decode(store.get(CHECKPOINT_KEY, ""));
    }

    /**
     * Records where a level began, unless the stored checkpoint is already further along.
     *
     * That one comparison is what lets Level Select replay level two without wiping out a level
     * eight Continue, and it means a fresh run that dies early costs nothing either. Keeping the
     * rule here rather than at the call sites is what lets every caller fire unconditionally.
     *
     * Battle mode never checkpoints: it has no levels to sit between.
     */
    public void saveCheckpoint(SaveSlot at) {
        if (at == null || at.mode() == GameMode.BATTLE) {
            return;
        }
        Optional<SaveSlot> held = checkpoint();
        boolean regression = held.isPresent()
                && held.get().mode() == at.mode()
                && held.get().progress() >= at.progress();
        if (regression) {
            return;
        }
        store.put(CHECKPOINT_KEY, at.encode());
        flush();
    }

    /** @param number 1..{@link #SLOTS}; anything else reads as empty */
    public Optional<SaveSlot> slot(int number) {
        if (number < 1 || number > SLOTS) {
            return Optional.empty();
        }
        return SaveSlot.decode(store.get(SLOT_KEY_PREFIX + number, ""));
    }

    /** Writes a manual slot outright. No no-regress rule here: the player asked for it. */
    public void save(int number, SaveSlot state) {
        if (number < 1 || number > SLOTS || state == null) {
            return;
        }
        store.put(SLOT_KEY_PREFIX + number, state.encode());
        flush();
    }

    private void flush() {
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Losing a checkpoint is not worth interrupting play over.
        }
    }
}
