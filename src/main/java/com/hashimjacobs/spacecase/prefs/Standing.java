package com.hashimjacobs.spacecase.prefs;

/**
 * A pilot's rank after a level's bonuses were credited to their career.
 *
 * @param pilotName   who it belongs to
 * @param rank        what they hold now
 * @param careerScore career total behind that rank
 * @param promoted    whether this level is what earned it
 */
public record Standing(String pilotName, Rank rank, int careerScore, boolean promoted) {

    /** Progress from this rank toward the next, as a 0..1 fraction. 1 at the top of the ladder. */
    public double progress() {
        double earned = rank.progressToward(rank.next(), careerScore);
        return earned;
    }
}
