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
    SPAWNER(0, 150, 0),

    /**
     * A full ring whose centre rotates and whose spread breathes in and out.
     *
     * The campaign's last pattern, and the only one that covers every direction at once: where RING
     * spans the downward half because a full circle would send half its shots straight off the top
     * of the arena to be culled, this one is fired by something sitting in the middle of the arena,
     * so every direction has arena in it.
     *
     * Twelve shots at this spread span very nearly the whole circle. The spread is the widest the
     * pattern ever draws, not a constant -- see {@link #spreadRadiansAt}.
     */
    VORTEX(12, 30, Math.PI / 6);

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

    /** Angle between adjacent shots at its widest, in radians. */
    public double spreadRadians() {
        return spreadRadians;
    }

    /** How tight VORTEX draws in at its tightest, as a fraction of its widest. */
    private static final double VORTEX_TIGHTEST = 0.55;

    /** Ticks for one full loosen-and-tighten of the vortex. Slow enough to be read and answered. */
    private static final double VORTEX_CYCLE_TICKS = 220;

    /**
     * The spread this phase is firing at right now.
     *
     * Every phase but VORTEX ignores the tick and returns its constant, so this is
     * {@link #spreadRadians()} for all six patterns that existed before it and no fight in the
     * first four galaxies changes.
     *
     * VORTEX needs it because a ring that only rotates is a ring: what makes it a vortex is the
     * gaps closing. That cannot live in {@code EnemyWeapons.centreAngleFor}, which returns the
     * centre angle and nothing else, and it cannot be read once per volley either -- the spread is
     * used twice per volley, once to place the first shot and once per shot after it, and both have
     * to agree or the pattern is no longer centred on where it is aimed.
     */
    public double spreadRadiansAt(int tick) {
        if (this != VORTEX) {
            return spreadRadians;
        }
        double breathe = 0.5 + 0.5 * Math.cos(2 * Math.PI * tick / VORTEX_CYCLE_TICKS);
        return spreadRadians * (VORTEX_TIGHTEST + (1 - VORTEX_TIGHTEST) * breathe);
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
