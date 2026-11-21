package com.hashimjacobs.spacecase.net;

import java.util.Optional;

import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * What the room a pilot is standing in was opened for, as the relay tells everyone in it.
 *
 * The same facts a {@link GroupListing} carries on the board, minus the ones a pilot inside the
 * room already has by other means: its code, and who is in it.
 *
 * @param playing whether a level is under way, in which case a newcomer waits for the next one
 */
public record GroupInfo(boolean isPublic, int maxPlayers, Level level, Difficulty difficulty,
                        boolean playing) {

    /**
     * Reads a {@code group,<visibility>,<max>,<galaxy>,<level>,<difficulty>,<status>} line, already
     * split. Empty for a line naming a level or difficulty this build has no table for: a room
     * this client cannot describe is one it cannot fly in either.
     */
    static Optional<GroupInfo> parse(String[] fields) {
        if (fields.length < 7) {
            return Optional.empty();
        }
        try {
            return Optional.of(new GroupInfo("PUBLIC".equals(fields[1]), Integer.parseInt(fields[2]),
                    Level.valueOf(fields[4]), Difficulty.valueOf(fields[5]),
                    "PLAYING".equals(fields[6])));
        } catch (IllegalArgumentException unknown) {
            // NumberFormatException and both valueOf calls land here.
            return Optional.empty();
        }
    }

    /** One line for the lobby: public or private, where, how hard. */
    public String summary() {
        return (isPublic ? "PUBLIC" : "PRIVATE") + "  \u00b7  " + GroupListing.place(level)
                + "  \u00b7  " + difficulty.label().toUpperCase();
    }
}
