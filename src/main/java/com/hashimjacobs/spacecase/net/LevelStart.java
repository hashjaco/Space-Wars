package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.SaveSlot;

/**
 * Everything the peers must agree on before a fight, sent once by the host.
 *
 * This is the whole of the coordination that lockstep needs. During a fight nothing crosses the
 * wire but input; between fights nothing crosses it but this. Every field here is something that
 * would otherwise be read from a local machine and would therefore differ: the seed drives every
 * spawn, the difficulty sets enemy fire rate and the spawn cap, and the loadouts change each ship's
 * damage, hull and speed.
 *
 * <h2>Why semicolons</h2>
 *
 * {@link SaveSlot} and {@link Loadout} are both comma-separated, and {@code SaveSlot}'s own javadoc
 * says why it refuses to nest one inside the other: it "would force a second separator and
 * positional parsing games". This envelope is that second separator, chosen rather than stumbled
 * into. {@code Loadout} already spends the comma, the pipe and the equals sign, so the semicolon is
 * what is left.
 *
 * @param seed      what every peer's shared {@code Random} starts from
 * @param resumeTick the tick the fight begins on, so machines that spent different amounts of time
 *                  in the garage still agree about the clock the simulation reads
 * @param loadouts  one encoded {@link Loadout} per player, in player-number order
 */
public record LevelStart(long seed, int resumeTick, Difficulty difficulty, SaveSlot slot,
                         List<String> loadouts) {

    /** Bumped only if the field order changes incompatibly. Appending a field is not a break. */
    private static final int FORMAT_VERSION = 1;

    private static final String SEPARATOR = ";";

    /** Version, seed, resume tick, difficulty, save slot. */
    private static final int HEADER_FIELDS = 5;

    public LevelStart {
        loadouts = List.copyOf(loadouts);
    }

    public String encode() {
        StringBuilder code = new StringBuilder()
                .append(FORMAT_VERSION)
                .append(SEPARATOR).append(seed)
                .append(SEPARATOR).append(resumeTick)
                .append(SEPARATOR).append(difficulty.name())
                .append(SEPARATOR).append(slot.encode());
        for (String loadout : loadouts) {
            code.append(SEPARATOR).append(loadout);
        }
        return code.toString();
    }

    /**
     * Reads back an {@link #encode}d start, or nothing at all.
     *
     * Never throws, and never falls back to a default. {@code Loadout.decode} can default because a
     * stock ship is a valid ship; there is no such thing as a valid default fight. A peer that
     * cannot read the host's start message must refuse to play rather than simulate its own guess,
     * because a guess here is a desync that looks like a bug in the game.
     */
    public static Optional<LevelStart> decode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        // Limit -1 so a trailing empty loadout is kept: a stock ship encodes short, and dropping
        // the field would hand player two player three's ship.
        String[] fields = code.split(SEPARATOR, -1);
        if (fields.length < HEADER_FIELDS) {
            return Optional.empty();
        }
        try {
            if (Integer.parseInt(fields[0]) != FORMAT_VERSION) {
                return Optional.empty();
            }
            Optional<SaveSlot> slot = SaveSlot.decode(fields[4]);
            if (slot.isEmpty()) {
                return Optional.empty();
            }
            List<String> loadouts = new ArrayList<>();
            for (int i = HEADER_FIELDS; i < fields.length; i++) {
                loadouts.add(fields[i]);
            }
            return Optional.of(new LevelStart(
                    Long.parseLong(fields[1]),
                    Integer.parseInt(fields[2]),
                    Difficulty.valueOf(fields[3]),
                    slot.get(),
                    loadouts));
        } catch (IllegalArgumentException unreadable) {
            // NumberFormatException and Difficulty.valueOf both land here.
            return Optional.empty();
        }
    }
}
