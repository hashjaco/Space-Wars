package com.hashimjacobs.spacecase.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * The choices on the create-group form, and the rule about which of them a pilot may make.
 *
 * No JavaFX here, for the reason {@link LobbyModel} gives. The rule is that a pilot can only ask
 * for help with somewhere they have reached: the galaxies and levels offered are the ones their
 * own campaign has unlocked, so a group cannot be a way around the campaign for its host.
 */
public final class GroupFormModel {

    private final Predicate<Level> levelUnlocked;
    private final List<Galaxy> galaxies = new ArrayList<>();

    private boolean isPublic = true;
    private int maxPlayers = GroupSpec.MAX_PLAYERS;
    private Galaxy galaxy;
    private Level level;
    private Difficulty difficulty;

    /**
     * @param galaxyUnlocked which galaxies this pilot has reached; the first is always offered
     * @param levelUnlocked  which levels this pilot may start a run on
     * @param initial        the difficulty the pilot already plays on, as the starting choice
     */
    public GroupFormModel(Predicate<Galaxy> galaxyUnlocked, Predicate<Level> levelUnlocked,
                          Difficulty initial) {
        this.levelUnlocked = levelUnlocked;
        for (Galaxy candidate : Galaxy.values()) {
            if (candidate.ordinal() == 0 || galaxyUnlocked.test(candidate)) {
                galaxies.add(candidate);
            }
        }
        this.galaxy = galaxies.get(0);
        this.level = galaxy.first();
        this.difficulty = initial;
    }

    public void cycleVisibility() {
        isPublic = !isPublic;
    }

    /** Two, three, four, and round again. */
    public void cycleMax(int direction) {
        int span = GroupSpec.MAX_PLAYERS - GroupSpec.MIN_PLAYERS + 1;
        maxPlayers = GroupSpec.MIN_PLAYERS
                + Math.floorMod(maxPlayers - GroupSpec.MIN_PLAYERS + step(direction), span);
    }

    /** Moves to another galaxy and lands on its first level, which reaching the galaxy unlocked. */
    public void cycleGalaxy(int direction) {
        int at = Math.floorMod(galaxies.indexOf(galaxy) + step(direction), galaxies.size());
        galaxy = galaxies.get(at);
        level = galaxy.first();
    }

    /** The next level in this galaxy the pilot has reached, skipping any they have not. */
    public void cycleLevel(int direction) {
        List<Level> levels = galaxy.levels();
        int at = levels.indexOf(level);
        for (int tried = 0; tried < levels.size(); tried++) {
            at = Math.floorMod(at + step(direction), levels.size());
            Level candidate = levels.get(at);
            if (candidate == galaxy.first() || levelUnlocked.test(candidate)) {
                level = candidate;
                return;
            }
        }
    }

    public void cycleDifficulty(int direction) {
        Difficulty[] all = Difficulty.values();
        difficulty = all[Math.floorMod(difficulty.ordinal() + step(direction), all.length)];
    }

    /** A press with no direction, such as Enter on the row, moves forward. */
    private static int step(int direction) {
        return direction < 0 ? -1 : 1;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public Galaxy galaxy() {
        return galaxy;
    }

    public Level level() {
        return level;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public GroupSpec spec() {
        return new GroupSpec(isPublic, maxPlayers, level, difficulty);
    }
}
