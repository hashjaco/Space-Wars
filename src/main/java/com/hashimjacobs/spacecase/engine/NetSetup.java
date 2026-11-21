package com.hashimjacobs.spacecase.engine;

import java.util.List;

import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * Everything about a networked run that a loop would otherwise read from its own machine.
 *
 * Every peer builds the whole game, and anything built from local preferences is built differently
 * on each: the difficulty preset, the names on the ships, the garage each ship was last fitted
 * from. A loop handed one of these reads all of that from the host's terms instead, which is what
 * lets a newcomer construct, from nothing but a start message, the same world the others hold.
 *
 * @param difficulty   the host's preset, never this machine's
 * @param pilotNames   one per ship, in ship order; decides how many ships there are
 * @param loadoutCodes one encoded loadout per ship, in ship order
 * @param resumeTick   the tick the fight opens on, the same on every machine
 * @param localSeat    the ship number (1-based, dense) this machine's pilot flies
 */
public record NetSetup(Difficulty difficulty, List<String> pilotNames, List<String> loadoutCodes,
                       int resumeTick, int localSeat) {

    public NetSetup {
        pilotNames = List.copyOf(pilotNames);
        loadoutCodes = List.copyOf(loadoutCodes);
        if (localSeat < 1 || localSeat > pilotNames.size()) {
            throw new IllegalArgumentException("local seat " + localSeat + " of " + pilotNames.size());
        }
    }
}
