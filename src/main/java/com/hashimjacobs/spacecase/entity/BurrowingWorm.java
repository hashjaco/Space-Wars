package com.hashimjacobs.spacecase.entity;

/**
 * The Dune Leviathan: a maw that lunges out of the right-hand wall and withdraws into it.
 *
 * One hitbox, unlike the hydra. That is a choice rather than a shortcut -- the multi-part
 * machinery is already there and reusing it would cost almost nothing, but then both new bosses
 * would be the same fight. A worm's identity is the lunge, not being taken apart.
 *
 * ponytail: if the lunge turns out to have no counterplay, promote the three rearmost segments to
 * {@link BossHead}s. About twenty lines, because the parts machinery already exists.
 */
public final class BurrowingWorm extends EnemyShip {

    /** Rings trailing the maw. Drawn, not simulated -- see {@link #trailX}. */
    public static final int SEGMENTS = 9;

    /**
     * Ticks of body between one ring and the next.
     *
     * Short, because the gap between rings is this multiplied by however fast the worm is moving.
     * At the top of a lunge it covers around ten pixels a tick, so nine ticks left rings a hundred
     * pixels apart -- a string of separate discs rather than one animal. Five keeps them
     * overlapping through the fastest part of the strike.
     */
    private static final int SEGMENT_LAG = 5;

    /** One full strike and withdrawal, a little over four seconds. */
    private static final int CYCLE = 260;

    /**
     * Ticks for one pass of the vertical drift.
     *
     * Near-coprime with {@link #CYCLE} on purpose: the two cycles take a long time to realign, so
     * consecutive strikes arrive in different lanes and the fight cannot be stood still through.
     */
    private static final double DRIFT_TICKS = 173;

    /** Where the burrow mouth sits, and how far into the arena a strike reaches. */
    private static final double REST_DEPTH = 90;

    /**
     * How far across the arena a strike reaches, as a fraction of its depth.
     *
     * A fraction rather than a fixed distance because the arena is not square: down-arena is 864
     * and across is 996, so a worm striking from the top of a top-down level and one striking from
     * the side of a side-on level need different absolute reaches to look the same. This used to be
     * {@code WIDTH * 0.62}, which was right only for the side-on level it was written for.
     */
    private static final double STRIKE_REACH = 0.62;

    private int age;

    public BurrowingWorm(Boss boss, double x, double y, double scale) {
        super(boss, x, y, scale, boss.art().width(), boss.art().height(),
                (int) Math.round(boss.health() * scale), boss.scoreValue());
    }

    /** The lunge script owns where this thing is; there is nothing to track. */
    @Override
    public void trackAcross(Entity target) {
    }

    /**
     * Deliberately does not call {@code super.update()}: the inherited clamp is station-keeping
     * for a boss that holds a line, and this one is supposed to charge past it and come back.
     */
    @Override
    public void update() {
        age++;
        Orientation facing = orientation();
        double depth = lungeDepth(age);
        double across = driftAcross(age);
        setPosition(facing.atX(depth, across, width(), height()),
                facing.atY(depth, across, width(), height()));
    }

    /**
     * Where trailing ring {@code k} is, in world coordinates.
     *
     * The body costs no state at all: position is a pure function of age, so a ring is simply that
     * same function evaluated further back in time. Burrowing falls out for free -- a negative age
     * evaluates to the rest pose inside the wall, so the tail is always underground.
     */
    public double trailX(int k) {
        int when = age - k * SEGMENT_LAG;
        return orientation().atX(lungeDepth(when), driftAcross(when), width(), height())
                + width() / 2;
    }

    public double trailY(int k) {
        int when = age - k * SEGMENT_LAG;
        return orientation().atY(lungeDepth(when), driftAcross(when), width(), height())
                + height() / 2;
    }

    /** Fast out, a beat with the jaws in the arena, then a slow withdrawal. */
    private double lungeDepth(double at) {
        if (at < 0) {
            // Before the fight started, which is where the trailing rings are on arrival. Held at
            // the burrow mouth rather than wrapped: floorMod would put them mid-strike, and the
            // tail would come out of the wall ahead of the head.
            return REST_DEPTH;
        }
        double progress = Math.floorMod((long) at, CYCLE) / (double) CYCLE;
        double out = progress < 0.20 ? smooth(progress / 0.20)
                : progress < 0.45 ? 1
                : 1 - smooth((progress - 0.45) / 0.55);
        return REST_DEPTH + (strikeDepth() - REST_DEPTH) * out;
    }

    /** How far in a strike reaches, in pixels, for whichever way this level runs. */
    private double strikeDepth() {
        return orientation().arenaDepth() * STRIKE_REACH;
    }

    private double driftAcross(double at) {
        double breadth = orientation().arenaBreadth();
        return breadth * 0.5 + Math.sin(at / DRIFT_TICKS) * breadth * 0.30
                - orientation().acrossExtent(width(), height()) / 2;
    }

    /** Smoothstep, so the strike accelerates and settles instead of snapping. */
    private static double smooth(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return clamped * clamped * (3 - 2 * clamped);
    }
}
