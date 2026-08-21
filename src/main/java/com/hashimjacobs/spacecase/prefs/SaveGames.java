package com.hashimjacobs.spacecase.prefs;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * Where runs are kept: one automatic checkpoint, three slots the player writes by hand, and which
 * levels have been cleared.
 *
 * The checkpoint is what "Continue" resumes. It is written every time a level begins, so nobody has
 * to remember to save; the manual slots exist for the things an automatic one cannot do, like
 * keeping a co-op run while you play solo.
 *
 * The cleared-levels mask is what the universe map gates on, and it is a separate thing from the
 * checkpoint on purpose. A checkpoint says where you are; it cannot say where you have been. Unlocks
 * used to be derived from it -- "every level up to the one you reached" -- which was true enough for
 * one ladder of ten but cannot express a galaxy you finished, left, and came back to. So clearing a
 * level is now recorded, once, when its flagship dies.
 *
 * A few string values in a preferences node. {@code Pilots} already stores an encoded object this
 * way and the payload here is about a hundred characters against an eight-thousand limit.
 */
public final class SaveGames {

    /** Manual slots offered on the save and load screens. */
    public static final int SLOTS = 3;

    private static final String CHECKPOINT_KEY = "checkpoint";
    private static final String SLOT_KEY_PREFIX = "slot";
    private static final String CLEARED_KEY_PREFIX = "cleared/";

    private final Preferences store;

    private SaveGames(Preferences store) {
        this.store = store;
    }

    public static SaveGames load() {
        Preferences store = Preferences.userNodeForPackage(SaveGames.class).node("saves");
        SaveGames saves = new SaveGames(store);
        saves.grantFromCheckpoint();
        return saves;
    }

    /**
     * Loads from an explicit node instead of the user's real one.
     *
     * Public so that tests outside this package -- the universe map's, for one -- can build an
     * isolated store rather than writing into the player's actual progress. Taking the node as an
     * argument is what makes that safe; there is nothing to get wrong.
     */
    public static SaveGames load(Preferences store) {
        SaveGames saves = new SaveGames(store);
        saves.grantFromCheckpoint();
        return saves;
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
                && deeper(at, held.get()) <= 0;
        if (regression) {
            return;
        }
        store.put(CHECKPOINT_KEY, at.encode());
        flush();
    }

    /**
     * Which of two runs is further along: the later loop, then the later level.
     *
     * Lexicographic rather than one folded number. Folding multiplies the loop by the campaign's
     * length, and the campaign grows -- a looped save written when it was ten levels long scored
     * past everything playable once it was forty, so the rule above froze it in place forever.
     */
    private static int deeper(SaveSlot a, SaveSlot b) {
        return a.loop() != b.loop() ? Integer.compare(a.loop(), b.loop())
                : Integer.compare(a.level().ordinal(), b.level().ordinal());
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

    /**
     * Erases every run and all campaign progress: the checkpoint, the manual slots and the cleared
     * masks for both modes.
     *
     * Deliberately only this node. High scores, pilots and their credits are a record of what was
     * played rather than of where the campaign stands, and settings are not progress at all -- a
     * player asking to start the campaign over is not asking to be logged out of their own pilot.
     */
    public void resetProgress() {
        try {
            store.clear();
        } catch (BackingStoreException e) {
            // Nothing useful to do: the caller has already confirmed, and the same swallow the
            // rest of this class makes for a failed flush applies here.
        }
        flush();
    }

    // ---- Cleared levels ----------------------------------------------------------------------
    //
    // One bit per level, indexed by Level.ordinal(), held as hex in one string per mode. Fifty
    // levels is fifty bits, so a long carries the whole campaign and there is no chunking to get
    // wrong. Indexing by ordinal rather than by (galaxy, level) is deliberate: galaxies are
    // contiguous blocks of ten in enum order, so the two would compute the same number, and the
    // only thing a second way of computing it can do is disagree with the first.
    //
    // Appending levels never moves an existing bit, which is what makes the campaign safe to grow.
    // Reordering or inserting them would hand every player somebody else's progress.

    /** Which levels this mode has cleared, as a bit per {@code Level.ordinal()}. */
    public long clearedMask(GameMode mode) {
        if (mode == null) {
            return 0;
        }
        String held = store.get(CLEARED_KEY_PREFIX + mode.name(), "");
        try {
            return Long.parseUnsignedLong(held, 16);
        } catch (NumberFormatException e) {
            // Same bargain SaveSlot.decode makes: unreadable progress reads as none rather than
            // taking the game down. Losing unlocks is bad; refusing to start is worse.
            return 0;
        }
    }

    public boolean isCleared(GameMode mode, Level level) {
        return level != null && (clearedMask(mode) & bit(level)) != 0;
    }

    /**
     * Records a cleared level. Called when the flagship dies, from {@code engine.GameLoop}.
     *
     * Battle mode is never recorded, for the same reason it is never checkpointed: it has no
     * levels to progress through.
     */
    public void recordClear(GameMode mode, Level level) {
        if (mode == null || mode == GameMode.BATTLE || level == null) {
            return;
        }
        long held = clearedMask(mode);
        long updated = held | bit(level);
        if (updated == held) {
            return;
        }
        write(mode, updated);
    }

    /**
     * Whether a galaxy can be entered: the first one always, otherwise the whole of the one before.
     *
     * The whole of it, not just its last level, so a player cannot skip a galaxy's middle by
     * replaying its finale.
     */
    public boolean isGalaxyUnlocked(GameMode mode, Galaxy galaxy) {
        if (galaxy == null) {
            return false;
        }
        if (galaxy.ordinal() == 0) {
            return true;
        }
        long required = galaxyMask(Galaxy.values()[galaxy.ordinal() - 1]);
        return (clearedMask(mode) & required) == required;
    }

    /** Whether a level can be flown: its galaxy is open, and the level before it is cleared. */
    public boolean isUnlocked(GameMode mode, Level level) {
        if (level == null || !isGalaxyUnlocked(mode, level.galaxy())) {
            return false;
        }
        if (level.indexInGalaxy() == 1) {
            return true;
        }
        Level previous = Level.values()[level.ordinal() - 1];
        return isCleared(mode, previous);
    }

    /** How many of a galaxy's ten are done, for the map's "7/10" caption. */
    public int clearedCount(GameMode mode, Galaxy galaxy) {
        if (galaxy == null) {
            return 0;
        }
        return Long.bitCount(clearedMask(mode) & galaxyMask(galaxy));
    }

    /**
     * Grants what a pre-galaxy checkpoint implies, so an existing player keeps what they earned.
     *
     * Before this mask existed, a checkpoint was the only record of progress and unlocks were read
     * off it as "everything up to the level you reached". That is exactly recoverable: a checkpoint
     * at stage N means stages 0..N-1 were cleared. Reached, not cleared -- the checkpoint is written
     * when a level *begins*, so the level it names is the one in progress and does not count.
     *
     * Runs on every load and needs no version key, because it only ever adds bits: once the mask is
     * at or past what the checkpoint implies, the or-equals below changes nothing.
     */
    private void grantFromCheckpoint() {
        repairLegacyCheckpoint();
        checkpoint().ifPresent(save -> {
            // The level's own ordinal, not progress(): that folds the loop counter in, so it is
            // not a level count at all. Reading it as one gave a looped save the whole campaign as
            // it stands *now*, which hands out every galaxy that shipped after it was written.
            int reached = save.level().ordinal();
            if (save.loop() > 1) {
                // Looping was only reachable when the campaign was a single galaxy long, so that
                // is what a looped save can prove it cleared -- and no more.
                reached = Math.max(reached, Galaxy.LEVELS_PER_GALAXY);
            }
            long earned = (1L << reached) - 1;
            long held = clearedMask(save.mode());
            if ((held | earned) != held) {
                write(save.mode(), held | earned);
            }
        });
    }

    /**
     * Takes back what a version 1 checkpoint used to be granted, and brings it into this format.
     *
     * The migration above used to read {@code SaveSlot.progress()} as a level count. It is not one
     * -- it folds the loop counter in -- so a looped save reported a number past the end of the
     * campaign, which was clamped to the campaign's length and granted whole: every galaxy,
     * complete, unplayed.
     *
     * The version is what licenses undoing it. A version 1 record was written when the campaign was
     * a single galaxy long, so nothing in that store can honestly have cleared past the first
     * galaxy. The other way a checkpoint carries a loop is an endless run, which writes the current
     * version and is left alone -- so a genuinely finished campaign is never trimmed.
     *
     * Rewriting the checkpoint is the other half of the repair, and it does two jobs. Its inflated
     * progress outranked everything playable, so the no-regress rule froze it and Continue could
     * never move again; and dropping the version 1 marker is what stops an honest clear made after
     * this runs from being trimmed on the next load. Runs once for that reason -- afterwards there
     * is no legacy record left to match.
     */
    private void repairLegacyCheckpoint() {
        if (!SaveSlot.isLegacy(store.get(CHECKPOINT_KEY, ""))) {
            return;
        }
        checkpoint().ifPresent(save -> {
            long firstGalaxy = galaxyMask(Galaxy.values()[0]);
            long held = clearedMask(save.mode());
            if ((held & ~firstGalaxy) != 0) {
                write(save.mode(), held & firstGalaxy);
            }
            store.put(CHECKPOINT_KEY, new SaveSlot(save.mode(), save.level(),
                    save.wavesSurvived(), 1, save.players()).encode());
            flush();
        });
    }

    private void write(GameMode mode, long mask) {
        store.put(CLEARED_KEY_PREFIX + mode.name(), Long.toHexString(mask));
        flush();
    }

    private static long bit(Level level) {
        return 1L << level.ordinal();
    }

    private static long galaxyMask(Galaxy galaxy) {
        long ten = (1L << Galaxy.LEVELS_PER_GALAXY) - 1;
        return ten << (galaxy.ordinal() * Galaxy.LEVELS_PER_GALAXY);
    }

    private void flush() {
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Losing a checkpoint is not worth interrupting play over.
        }
    }
}
