package com.hashimjacobs.spacecase.mode;

import java.util.List;

/**
 * A group of ten levels with one identity, and the unit the campaign is gated in.
 *
 * A galaxy is a block of ten consecutive {@link Level} constants, so a level's galaxy is just its
 * ordinal divided by ten. Nothing maps the two together by hand, which means the two enums cannot
 * drift apart -- {@code GalaxyTest} pins the arithmetic that makes that true.
 *
 * Constants are appended, one per galaxy shipped, and never inserted. Two things depend on that:
 * the ordinal decides which ten levels a galaxy owns, and every saved cleared-levels mask is
 * indexed by level ordinal (see {@code prefs.SaveGames}). Inserting a galaxy would silently hand
 * every player somebody else's progress.
 *
 * A run is one galaxy. Ten levels is about half an hour; fifty in one sitting is not a session, and
 * ending at the border is what gives clearing a galaxy the weight of an ending rather than a wave
 * counter ticking over. So {@link #next()} returns null at the end of the campaign rather than
 * wrapping, unlike {@link Level#next()} -- the ten legs repeat, the campaign finishes.
 *
 * Pure data: no assets, no Preferences, no JavaFX. The accent is a hex string rather than a Color
 * for that last reason -- see {@code ui.Tokens} for why the interface layer cares.
 */
public enum Galaxy {

    VERDANCE("Verdance", "#0ec417");

    /**
     * Levels in a galaxy. Not a suggestion -- the block arithmetic below assumes it exactly, and
     * {@code GalaxyTest} refuses to let the level count stop being a multiple of it.
     */
    public static final int LEVELS_PER_GALAXY = 10;

    private final String label;
    private final String accent;

    Galaxy(String label, String accent) {
        this.label = label;
        this.accent = accent;
    }

    public String label() {
        return label;
    }

    /** Position in the campaign, counting from one, for display. */
    public int number() {
        return ordinal() + 1;
    }

    /**
     * This galaxy's colour, as a hex string.
     *
     * A string rather than a {@code javafx.scene.paint.Color} so this enum stays usable in tests
     * that never start the toolkit. The screens that draw it parse it once.
     */
    public String accent() {
        return accent;
    }

    /** The first level of this galaxy -- where a player who has just unlocked it starts. */
    public Level first() {
        return Level.values()[ordinal() * LEVELS_PER_GALAXY];
    }

    /** The last level of this galaxy. Clearing it is what unlocks the next one. */
    public Level last() {
        return Level.values()[ordinal() * LEVELS_PER_GALAXY + LEVELS_PER_GALAXY - 1];
    }

    /** This galaxy's ten levels, in run order. */
    public List<Level> levels() {
        int from = ordinal() * LEVELS_PER_GALAXY;
        return List.of(Level.values()).subList(from, from + LEVELS_PER_GALAXY);
    }

    /** The next galaxy, or null at the end of the campaign. */
    public Galaxy next() {
        Galaxy[] all = values();
        int following = ordinal() + 1;
        return following < all.length ? all[following] : null;
    }
}
