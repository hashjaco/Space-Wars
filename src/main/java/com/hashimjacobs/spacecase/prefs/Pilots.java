package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Who is flying, and what they have earned across every run.
 *
 * Career score is keyed by pilot name rather than by player slot, so a pilot keeps their {@link Rank}
 * whether they take the first or the second seat, and two people sharing a machine do not share a
 * career. The slot names themselves are stored separately, as the last thing each seat was set to.
 */
public final class Pilots {

    private static final String SLOT_KEY_PREFIX = "player";
    private static final String CAREER_NODE = "career";
    private static final String CREDITS_NODE = "credits";
    private static final String LOADOUT_NODE = "loadout";

    /** Longest name the entry screen accepts, and what the HUD is laid out for. */
    public static final int MAX_NAME_LENGTH = 10;

    private final Preferences store;
    private final Preferences careers;
    private final Preferences credits;
    private final Preferences loadouts;

    private Pilots(Preferences store) {
        this.store = store;
        this.careers = store.node(CAREER_NODE);
        this.credits = store.node(CREDITS_NODE);
        this.loadouts = store.node(LOADOUT_NODE);
    }

    public static Pilots load() {
        Preferences store = Preferences.userNodeForPackage(Pilots.class).node("pilots");
        Pilots pilots = new Pilots(store);
        return pilots;
    }

    /** Package-private so tests can supply a throwaway node. */
    static Pilots load(Preferences store) {
        Pilots pilots = new Pilots(store);
        return pilots;
    }

    public String name(int playerNumber) {
        String fallback = "PILOT " + playerNumber;
        String stored = store.get(SLOT_KEY_PREFIX + playerNumber, fallback);
        return stored;
    }

    /**
     * Whether this seat has been named, as opposed to {@link #name} returning its default.
     *
     * The entry screen needs the difference: an unnamed seat shows its default as a placeholder that
     * the first keystroke replaces, rather than as text to be typed onto the end of.
     */
    public boolean isNamed(int playerNumber) {
        boolean stored = store.get(SLOT_KEY_PREFIX + playerNumber, null) != null;
        return stored;
    }

    /**
     * Renames a seat.
     *
     * Blank input restores the default rather than leaving an empty label under the ship, and the
     * name is upper-cased and clipped because that is the only form the HUD and debrief draw.
     */
    public void setName(int playerNumber, String name) {
        String trimmed = name.trim().toUpperCase();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        if (trimmed.isEmpty()) {
            store.remove(SLOT_KEY_PREFIX + playerNumber);
        } else {
            store.put(SLOT_KEY_PREFIX + playerNumber, trimmed);
        }
        flush(store);
    }

    public int careerScore(String pilotName) {
        int earned = careers.getInt(pilotName, 0);
        return earned;
    }

    public Rank rank(String pilotName) {
        int earned = careerScore(pilotName);
        Rank standing = Rank.forCareerScore(earned);
        return standing;
    }

    /** Credits a level's score to a pilot's career and returns their new total. */
    public int addCareerScore(String pilotName, int amount) {
        int total = careerScore(pilotName) + Math.max(0, amount);
        careers.putInt(pilotName, total);
        flush(careers);
        return total;
    }

    /**
     * The pilot's garage balance.
     *
     * Kept apart from career score rather than spent out of it: career score sets {@link Rank} and
     * feeds the high-score table, and buying a paint job should not cost someone their standing.
     */
    public int credits(String pilotName) {
        int balance = credits.getInt(pilotName, 0);
        return balance;
    }

    /** Credits a level's earnings and returns the new balance. */
    public int addCredits(String pilotName, int amount) {
        int total = credits(pilotName) + Math.max(0, amount);
        setCredits(pilotName, total);
        return total;
    }

    /** Writes a balance outright, for the garage handing back what a pilot did not spend. */
    public void setCredits(String pilotName, int balance) {
        credits.putInt(pilotName, Math.max(0, balance));
        flush(credits);
    }

    /**
     * The pilot's ship as {@code garage.Loadout} encoded it, or empty if they never bought anything.
     *
     * Stored as an opaque string on purpose: this class knows how to keep a pilot's things, not
     * what an upgrade is, so the garage can change shape without touching preferences code.
     */
    public String loadoutCode(String pilotName) {
        String stored = loadouts.get(pilotName, "");
        return stored;
    }

    public void setLoadoutCode(String pilotName, String code) {
        loadouts.put(pilotName, code);
        flush(loadouts);
    }

    private static void flush(Preferences node) {
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // Losing a pilot's progress is not worth interrupting play over.
        }
    }
}
