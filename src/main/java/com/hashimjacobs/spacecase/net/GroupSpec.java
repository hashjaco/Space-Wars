package com.hashimjacobs.spacecase.net;

import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * What a pilot asks for when they open a group: who may find it, how many may come, and what
 * they want help with.
 *
 * @param isPublic   listed on the board, as opposed to found only by its code
 * @param maxPlayers two to four, the host included
 */
public record GroupSpec(boolean isPublic, int maxPlayers, Level level, Difficulty difficulty) {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = LobbyModel.MAX_PEERS;

    public GroupSpec {
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("a group seats two to four, not " + maxPlayers);
        }
    }

    /**
     * The body of {@code POST /group}: {@code visibility,max,galaxy,level,difficulty,name}.
     *
     * Enum names rather than ordinals, for the reason {@code SaveSlot} gives: a level added in the
     * middle of the campaign must not turn every open group into a different one.
     */
    public String encode(String hostName) {
        return (isPublic ? "PUBLIC" : "PRIVATE") + "," + maxPlayers + "," + level.galaxy().name()
                + "," + level.name() + "," + difficulty.name()
                // The comma is the separator. The relay cleans the name again; this only keeps a
                // stray one from turning six fields into seven and the request into a refusal.
                + "," + hostName.replace(",", " ");
    }
}
