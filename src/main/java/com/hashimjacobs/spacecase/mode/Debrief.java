package com.hashimjacobs.spacecase.mode;

import java.util.ArrayList;
import java.util.List;

/**
 * What one pilot did in a level, and what it paid them.
 *
 * Pure arithmetic over two snapshots of a player's counters, so the scoring rules are testable
 * without a world, a renderer or the JavaFX toolkit.
 *
 * @param pilotName          who flew it
 * @param levelNumber        position of the level in the run, counting from one
 * @param enemiesKilled      enemies destroyed during this level alone
 * @param asteroidsDestroyed asteroids destroyed during this level alone
 * @param shotsFired         projectiles launched during this level alone
 * @param shotsHit           of those, the ones that connected
 * @param damageTaken        damage absorbed during this level alone
 * @param livesLost          lives spent during this level alone
 * @param clearTicks         simulation steps the level took to clear
 * @param bonuses            what was earned, in the order it should be listed
 */
public record Debrief(
        String pilotName,
        int levelNumber,
        int enemiesKilled,
        int asteroidsDestroyed,
        int shotsFired,
        int shotsHit,
        int damageTaken,
        int livesLost,
        int clearTicks,
        List<Bonus> bonuses) {

    /** One earned line on the debrief screen. */
    public record Bonus(String label, int points) {
    }

    /** A player's running counters, sampled when a level starts and again when it is cleared. */
    public record Tally(int enemiesKilled, int asteroidsDestroyed, int shotsFired, int shotsHit,
                        int damageTaken, int lives) {
    }

    private static final int FLAGSHIP_BOUNTY_PER_LEVEL = 250;
    private static final int MARKSMAN_PER_PERCENT = 5;
    private static final int UNTOUCHED_BONUS = 1000;
    private static final int UNBROKEN_BONUS = 500;
    private static final int SWIFT_PER_TICK_SAVED = 2;

    /** Steps a second the simulation runs at, matching {@code engine.FixedTimestep}. */
    private static final int TICKS_PER_SECOND = 60;

    /**
     * Scores a cleared level from the counters before and after it.
     *
     * @param parTicks reference clear time; beating it pays the speed bonus
     */
    public static Debrief of(String pilotName, int levelNumber, int parTicks,
                             Tally before, Tally after, int clearTicks) {
        int enemies = after.enemiesKilled() - before.enemiesKilled();
        int asteroids = after.asteroidsDestroyed() - before.asteroidsDestroyed();
        int shots = after.shotsFired() - before.shotsFired();
        int hits = after.shotsHit() - before.shotsHit();
        int damage = after.damageTaken() - before.damageTaken();
        int livesLost = before.lives() - after.lives();
        int accuracy = accuracyPercent(shots, hits);
        int ticksSaved = Math.max(0, parTicks - clearTicks);

        List<Bonus> bonuses = new ArrayList<>();
        bonuses.add(new Bonus("Flagship bounty", FLAGSHIP_BOUNTY_PER_LEVEL * levelNumber));
        bonuses.add(new Bonus("Marksman  " + accuracy + "%", accuracy * MARKSMAN_PER_PERCENT));
        if (damage == 0) {
            bonuses.add(new Bonus("Untouched", UNTOUCHED_BONUS));
        }
        if (livesLost == 0) {
            bonuses.add(new Bonus("Unbroken", UNBROKEN_BONUS));
        }
        if (ticksSaved > 0) {
            bonuses.add(new Bonus("Swift", ticksSaved * SWIFT_PER_TICK_SAVED));
        }

        Debrief debrief = new Debrief(pilotName, levelNumber, enemies, asteroids, shots, hits,
                damage, livesLost, clearTicks, List.copyOf(bonuses));
        return debrief;
    }

    /**
     * Hit rate as a whole percentage.
     *
     * Zero when nothing was fired, so a level cleared by ramming pays no marksman bonus rather than
     * dividing by nothing.
     */
    public static int accuracyPercent(int shotsFired, int shotsHit) {
        if (shotsFired <= 0) {
            return 0;
        }
        int percent = Math.min(100, shotsHit * 100 / shotsFired);
        return percent;
    }

    public int accuracyPercent() {
        int percent = accuracyPercent(shotsFired, shotsHit);
        return percent;
    }

    /** What the bonuses add up to: added to the round score and to the pilot's career. */
    public int totalBonus() {
        int total = bonuses.stream().mapToInt(Bonus::points).sum();
        return total;
    }

    public int clearSeconds() {
        int seconds = clearTicks / TICKS_PER_SECOND;
        return seconds;
    }
}
