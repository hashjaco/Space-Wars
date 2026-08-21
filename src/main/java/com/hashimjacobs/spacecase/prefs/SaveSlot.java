package com.hashimjacobs.spacecase.prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * A run frozen at a level boundary: where the director had got to, and what each ship had done.
 *
 * Deliberately not the loadout. {@code engine.GameLoop} already fits whatever the pilot owns now
 * when a run starts, which is the whole point of replaying an old level -- you fly it again in a
 * better ship. Storing a second copy would only raise a which-one-wins question with nothing to
 * gain, and {@code garage.Loadout} is itself comma-separated, so nesting one here would force a
 * second separator and positional parsing games.
 *
 * Deliberately not the random seed either. A resumed level spawns a fresh sequence; pinning it
 * would turn level select into a memorisation exercise rather than another go at the same place.
 *
 * Encodes to one short string so {@code Pilots}-style preference storage is enough -- no file, no
 * IO error handling that the rest of this package does not have.
 */
public record SaveSlot(GameMode mode, Level level, int wavesSurvived, int loop,
                       List<PlayerShip.Progress> players) {

    /**
     * Bumped only if the field order below changes incompatibly. Appending is not a break.
     *
     * Version 2 stores the level by name where version 1 stored its ordinal. Both still read -- see
     * {@link #decode} -- so a save written before the campaign grew past one galaxy still resumes.
     */
    private static final int FORMAT_VERSION = 2;

    private static final String SEPARATOR = ",";

    /** Version, mode, level, waves, loop -- everything before the per-player groups. */
    private static final int HEADER_FIELDS = 5;
    private static final int FIELDS_PER_PLAYER = 8;

    public SaveSlot {
        players = List.copyOf(players);
    }

    /**
     * A level-select replay: this level, fresh ships, nothing carried over.
     *
     * The empty player list is load-bearing rather than lazy -- the restore loop is bounded by it,
     * so a replay needs no branch of its own anywhere downstream.
     */
    public static SaveSlot forReplay(GameMode mode, Level level) {
        return new SaveSlot(mode, level, 1, 1, List.of());
    }

    public String encode() {
        StringBuilder code = new StringBuilder()
                .append(FORMAT_VERSION)
                .append(SEPARATOR).append(mode.name())
                .append(SEPARATOR).append(level.name())
                .append(SEPARATOR).append(wavesSurvived)
                .append(SEPARATOR).append(loop);
        for (PlayerShip.Progress player : players) {
            code.append(SEPARATOR).append(player.health())
                    .append(SEPARATOR).append(player.lives())
                    .append(SEPARATOR).append(player.score())
                    .append(SEPARATOR).append(player.enemiesKilled())
                    .append(SEPARATOR).append(player.asteroidsDestroyed())
                    .append(SEPARATOR).append(player.shotsFired())
                    .append(SEPARATOR).append(player.shotsHit())
                    .append(SEPARATOR).append(player.damageTaken());
        }
        return code.toString();
    }

    /**
     * Reads back an {@link #encode}d run, or nothing at all.
     *
     * Never throws, and unlike {@code garage.Loadout.decode} it does not fall back to a default:
     * a stock ship is a valid ship, but there is no such thing as a valid default run. Anything
     * unrecognisable therefore reads as "no save", and the menu simply does not offer it -- which
     * is a better outcome than resuming someone into a level they never reached.
     */
    public static Optional<SaveSlot> decode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String[] fields = code.split(SEPARATOR);
        if (fields.length < HEADER_FIELDS
                || (fields.length - HEADER_FIELDS) % FIELDS_PER_PLAYER != 0) {
            return Optional.empty();
        }
        try {
            int version = Integer.parseInt(fields[0].trim());
            GameMode mode = modeNamed(fields[1].trim());
            // Read the level according to the version that wrote it. This has to happen before any
            // other field is parsed, because a version 1 record holds a number here and a version 2
            // record holds a name -- parsing first and branching after would throw on every v2 save.
            Level level = switch (version) {
                case 1 -> levelAt(Integer.parseInt(fields[2].trim()));
                case FORMAT_VERSION -> levelNamed(fields[2].trim());
                default -> null;
            };
            if (mode == null || level == null) {
                return Optional.empty();
            }
            int waves = Math.max(1, Integer.parseInt(fields[3].trim()));
            int loop = Math.max(1, Integer.parseInt(fields[4].trim()));

            List<PlayerShip.Progress> players = new ArrayList<>();
            for (int at = HEADER_FIELDS; at < fields.length; at += FIELDS_PER_PLAYER) {
                players.add(new PlayerShip.Progress(
                        Integer.parseInt(fields[at].trim()),
                        Integer.parseInt(fields[at + 1].trim()),
                        Integer.parseInt(fields[at + 2].trim()),
                        Integer.parseInt(fields[at + 3].trim()),
                        Integer.parseInt(fields[at + 4].trim()),
                        Integer.parseInt(fields[at + 5].trim()),
                        Integer.parseInt(fields[at + 6].trim()),
                        Integer.parseInt(fields[at + 7].trim())));
            }
            return Optional.of(new SaveSlot(mode, level, waves, loop, players));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }

    /** Null rather than an exception for a mode this build no longer has. */
    private static GameMode modeNamed(String name) {
        for (GameMode mode : GameMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * A level by name, which is what version 2 records store.
     *
     * Names rather than ordinals because the campaign grows: appending levels is safe either way,
     * but inserting or reordering one silently relocates every ordinal-keyed save to a different
     * level. {@code prefs.HighScores} already keyed on the name for that reason, so this also stops
     * the two stores disagreeing about what a save means.
     */
    private static Level levelNamed(String name) {
        for (Level level : Level.values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        return null;
    }

    /**
     * Whether an encoded record predates galaxies.
     *
     * Version is the only thing that says so, and it says it reliably: the campaign was one galaxy
     * of ten when version 1 was the format, so a version 1 record cannot have cleared past the
     * first galaxy however deep it looks. {@code SaveGames} leans on exactly that -- see the repair
     * in {@code grantFromCheckpoint} -- and the check lives here so the field order stays this
     * class's business.
     */
    static boolean isLegacy(String code) {
        return code != null && code.startsWith("1" + SEPARATOR);
    }

    /** Version 1 stored the ordinal. Kept so saves written before the campaign grew still load. */
    private static Level levelAt(int ordinal) {
        Level[] all = Level.values();
        if (ordinal < 0 || ordinal >= all.length) {
            return null;
        }
        return all[ordinal];
    }

    /** One line naming this run, for a menu row: "Co-op   Lv5 Undercity   12,400". */
    public String describe() {
        int best = 0;
        for (PlayerShip.Progress player : players) {
            best = Math.max(best, player.score());
        }
        String where = "Lv" + level.number() + " " + level.label();
        String tail = players.isEmpty() ? "" : "   " + best;
        return mode.label() + "   " + where + (loop > 1 ? "   loop " + loop : "") + tail;
    }
}
