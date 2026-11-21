package com.hashimjacobs.spacecase.net;

import java.util.Optional;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * One open group on the board.
 *
 * Everything here is what a room last told the relay about itself, so it can be a minute stale:
 * a row that says two of three may be full by the time it is pressed, and the join then fails in
 * the ordinary way a full room always has.
 *
 * @param code    the room code, which is how the group is joined
 * @param host    the pilot who opened it
 * @param players seats taken, including one held for a pilot whose connection dropped
 */
public record GroupListing(String code, String host, int players, int maxPlayers, Level level,
                           Difficulty difficulty, boolean playing) {

    /** code, host, players, max, galaxy, level, difficulty, status. */
    private static final int FIELDS = 8;

    /**
     * Reads one line of {@code GET /groups}, or nothing.
     *
     * A row naming a level or a difficulty this build does not have is skipped rather than shown:
     * it was posted by a newer game, and joining it would end at terms this one cannot read.
     */
    public static Optional<GroupListing> parse(String line) {
        String[] fields = line.trim().split(",");
        if (fields.length != FIELDS) {
            return Optional.empty();
        }
        try {
            Level level = Level.valueOf(fields[5]);
            if (level.galaxy() != Galaxy.valueOf(fields[4])) {
                return Optional.empty();
            }
            return Optional.of(new GroupListing(fields[0], fields[1],
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), level,
                    Difficulty.valueOf(fields[6]), "PLAYING".equals(fields[7])));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public Galaxy galaxy() {
        return level.galaxy();
    }

    /** Whether there is a seat to take. */
    public boolean isOpen() {
        return players < maxPlayers;
    }

    /** A level as a pilot would say it: the galaxy and how far into it. */
    static String place(Level level) {
        return level.galaxy().label().toUpperCase() + " " + level.indexInGalaxy();
    }

    /** The row's right side: where, how hard, how full. The host's name is the left. */
    public String value() {
        return place(level) + "  \u00b7  " + difficulty.label().toUpperCase()
                + "  \u00b7  " + players + "/" + maxPlayers;
    }

    /** The whole of it as a sentence, for the line under the list while this row has the cursor. */
    public String detail() {
        String what = host + " is flying " + level.galaxy().label() + " " + level.indexInGalaxy()
                + " on " + difficulty.label() + ".";
        if (!isOpen()) {
            return what + " Every seat is taken.";
        }
        return what + (playing ? " In flight: you join at the next level." : " Still in the lobby.");
    }
}
