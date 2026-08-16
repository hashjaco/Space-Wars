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
    private static final int UNBROKEN_BONUS = 500;
    private static final int SWIFT_PER_TICK_SAVED = 2;

    /**
     * Clearing the sky, in tiers.
     *
     * Sized against what a level actually fields: at NORMAL the director rolls a six-per-thousand
     * spawn chance each tick against a cap of six alive, so a four-to-five wave level offers
     * somewhere between twenty-five and forty-five targets. Ten is a comfortable pass, twenty is a
     * good level, thirty-five means you went looking.
     */
    private static final int PURGE_TIER_KILLS = 35;
    private static final int PURGE_TIER_BONUS = 2000;
    private static final int STRIKE_TIER_KILLS = 20;
    private static final int STRIKE_TIER_BONUS = 1000;
    private static final int SWEEP_TIER_KILLS = 10;
    private static final int SWEEP_TIER_BONUS = 400;

    /**
     * Flying clean, in tiers.
     *
     * A perfect level pays the largest single bonus in the game, on purpose. The two lower rungs
     * exist because an all-or-nothing reward for exactly zero damage is invisible: a pilot who took
     * one hit in ten minutes learns nothing from it, and a pilot who took ninety cannot tell how
     * close they were. Exactly one of the three is ever awarded.
     */
    private static final int UNTOUCHED_BONUS = 2500;
    private static final int UNSCATHED_DAMAGE = 20;
    private static final int UNSCATHED_BONUS = 1000;
    private static final int GRAZED_DAMAGE = 60;
    private static final int GRAZED_BONUS = 400;

    /**
     * How a level's bonus converts into garage credits.
     *
     * A divisor rather than a separate calculation, so playing well pays for upgrades and the two
     * reward systems cannot drift apart. The flat per-level term is a floor: a scrappy clear still
     * buys something, which matters most on the early levels where the bonus is smallest.
     *
     * There is a feedback loop here worth naming: plating reduces recorded damage, which earns a
     * clean-flying bonus, which buys more plating. The tiers above make it stronger than it was
     * when only a perfect level paid out -- the divisor is what holds it in check, and is why it
     * is 50 rather than the 25 it was before the tiers existed. Move one and check the other.
     */
    private static final int CREDIT_DIVISOR = 50;
    private static final int CREDITS_PER_LEVEL = 10;

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
        // Both ladders carry their number in the label, as Marksman does, so a near miss reads as
        // a near miss rather than as nothing at all.
        int killBonus = killTierFor(enemies);
        if (killBonus > 0) {
            bonuses.add(new Bonus("Purge  " + enemies + " kills", killBonus));
        }
        if (damage == 0) {
            bonuses.add(new Bonus("Untouched", UNTOUCHED_BONUS));
        } else if (damage <= UNSCATHED_DAMAGE) {
            bonuses.add(new Bonus("Unscathed  " + damage + " dmg", UNSCATHED_BONUS));
        } else if (damage <= GRAZED_DAMAGE) {
            bonuses.add(new Bonus("Grazed  " + damage + " dmg", GRAZED_BONUS));
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

    /** The highest kill tier this many kills reaches, or zero below the lowest rung. */
    private static int killTierFor(int enemies) {
        if (enemies >= PURGE_TIER_KILLS) {
            return PURGE_TIER_BONUS;
        }
        if (enemies >= STRIKE_TIER_KILLS) {
            return STRIKE_TIER_BONUS;
        }
        if (enemies >= SWEEP_TIER_KILLS) {
            return SWEEP_TIER_BONUS;
        }
        return 0;
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

    /** What this level pays into the pilot's garage account, on top of their score. */
    public int credits() {
        int earned = totalBonus() / CREDIT_DIVISOR + levelNumber * CREDITS_PER_LEVEL;
        return earned;
    }

    public int clearSeconds() {
        int seconds = clearTicks / TICKS_PER_SECOND;
        return seconds;
    }
}
