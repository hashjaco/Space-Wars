package com.hashimjacobs.spacecase.entity;

/**
 * How the boss fights, chosen by how much health it has left.
 *
 * The boss used to fire the same single shot as the weakest scout, which made a 600-health enemy a
 * pure damage sponge rather than a fight.
 */
public enum BossPhase {

    /** Opening: a wide, sparse spread that is easy to read and slip between. */
    SPREAD(3, 26, 0.36, 44),

    /** Middle: a fan that sweeps side to side, so standing still stops working. */
    SWEEPING_FAN(5, 18, 0.22, 30),

    /** Final: tight bursts aimed at the nearest player, fired fast. */
    AIMED_BURST(3, 12, 0.14, 18);

    private final int shots;
    private final int cooldownTicks;
    private final double spreadRadians;
    private final int aimJitterDegrees;

    BossPhase(int shots, int cooldownTicks, double spreadRadians, int aimJitterDegrees) {
        this.shots = shots;
        this.cooldownTicks = cooldownTicks;
        this.spreadRadians = spreadRadians;
        this.aimJitterDegrees = aimJitterDegrees;
    }

    /** Phase for a boss at the given 0..1 remaining-health fraction. */
    public static BossPhase forHealthFraction(double fraction) {
        if (fraction > 0.66) {
            return SPREAD;
        }
        if (fraction > 0.33) {
            return SWEEPING_FAN;
        }
        return AIMED_BURST;
    }

    public int shots() {
        return shots;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Angle between adjacent shots, in radians. */
    public double spreadRadians() {
        return spreadRadians;
    }

    public int aimJitterDegrees() {
        return aimJitterDegrees;
    }

    /** True when the whole pattern should rotate over time rather than firing straight down. */
    public boolean sweeps() {
        boolean sweeping = this == SWEEPING_FAN;
        return sweeping;
    }

    /** True when the pattern should be aimed at a player rather than fired downward. */
    public boolean aimsAtPlayer() {
        boolean aimed = this == AIMED_BURST;
        return aimed;
    }
}
