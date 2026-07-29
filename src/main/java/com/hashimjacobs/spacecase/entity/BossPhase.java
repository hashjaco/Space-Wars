package com.hashimjacobs.spacecase.entity;

/**
 * How a boss fights during one third of its health bar.
 *
 * Which three phases a boss uses, and in what order, is {@link Boss}'s business -- a phase only
 * describes a pattern. Bosses used to fire the same single shot as the weakest scout, which made a
 * 600-health enemy a damage sponge rather than a fight.
 */
public enum BossPhase {

    /** A wide, sparse spread that is easy to read and slip between. */
    SPREAD(3, 26, 0.36),

    /** A fan that sweeps side to side, so standing still stops working. */
    SWEEPING_FAN(5, 18, 0.22),

    /** Tight bursts aimed at the nearest player, fired fast. */
    AIMED_BURST(3, 12, 0.14),

    /**
     * A half-circle curtain covering everything below the boss.
     *
     * A full 360 degree ring would send half its shots off the top of the arena to be culled
     * immediately, so this spans the downward half only: {@code shots - 1} gaps across pi radians.
     */
    RING(9, 34, Math.PI / 8),

    /** A narrow stream whose angle rotates continuously, painting an arc across the arena. */
    SPIRAL(4, 10, 0.30),

    /** Fires nothing and calls in escort fighters instead. */
    SPAWNER(0, 150, 0);

    private final int shots;
    private final int cooldownTicks;
    private final double spreadRadians;

    BossPhase(int shots, int cooldownTicks, double spreadRadians) {
        this.shots = shots;
        this.cooldownTicks = cooldownTicks;
        this.spreadRadians = spreadRadians;
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
