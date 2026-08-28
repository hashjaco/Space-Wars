package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * A maw that lunges out of the wall it lives in and withdraws into it.
 *
 * Two of them: the Dune Leviathan out of the right-hand wall of a side-on level, and the Storm
 * Serpent down out of the cloud deck of a top-down one. The second cost this class nothing, because
 * it holds no orientation of its own -- every distance it uses is asked of {@link Orientation}, so
 * the axis of the strike is whatever the level runs on. What the two do not share is how far a
 * strike reaches; see {@link #strikeReach()}.
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
     * At the top of a lunge it covers around fourteen pixels a tick, so nine ticks left rings a
     * hundred and thirty pixels apart -- a string of separate discs rather than one animal. Four
     * keeps them overlapping through the fastest part of the strike.
     *
     * Was five, when a strike took 260 ticks rather than 190 and the peak was nearer ten pixels a
     * tick. It is the lag times the speed that has to stay put, so shortening one means shortening
     * the other. Nothing tests this -- it is a thing you look at.
     */
    private static final int SEGMENT_LAG = 4;

    /**
     * One full strike and withdrawal, a little over three seconds.
     *
     * Was 260. Shortened rather than deepened: the reach is what makes a strike fair, and it is
     * pinned to the pixel by StormSerpentTest, so the only honest way to make this animal more
     * dangerous is to have it strike more often.
     */
    private static final int CYCLE = 190;

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
    private static final double LEVIATHAN_REACH = 0.62;

    /**
     * The Storm Serpent's reach, which is shorter, and not by preference.
     *
     * A fraction of the depth is the right shape for this number but it is not the whole story: what
     * decides whether a strike is fair is where the *leading edge* of the maw ends up, and that is
     * the reach plus the art's extent along the strike axis. Those two differ between the levels,
     * because a side-on level measures depth across 996 and takes the art's width, and a top-down
     * one measures 864 and takes its height.
     *
     * Worked through: the Leviathan reaches 996 x 0.62 = 617.5 and adds its 214 of width, ending at
     * 831.5 against a player who spawns 130 short of the back wall, at 866 -- about 35 pixels of
     * clearance. The Storm Serpent is 240 along its axis on an 864 arena, so the shared 0.62 would
     * put its leading edge at 775.7 and its jaws 40 pixels *past* the line the player starts on.
     * 0.53 lands it at 697.9, which is the same 36 pixels short that level 9 was tuned to.
     */
    private static final double SERPENT_REACH = 0.53;

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
        return orientation().arenaDepth() * strikeReach();
    }

    /**
     * This worm's reach, as a fraction of the arena's depth.
     *
     * Per-boss rather than one shared constant, so that giving the Storm Serpent a fair strike could
     * not retune the Dune Leviathan's -- level 9 has shipped, and its 0.62 is what it was tuned at.
     */
    private double strikeReach() {
        return boss() == Boss.STORM_SERPENT ? SERPENT_REACH : LEVIATHAN_REACH;
    }

    /**
     * The ring this worm's body is drawn from.
     *
     * Per-boss for a reason that is about the picture rather than the numbers: the Leviathan's ring
     * carries its bristles down one side, which is correct for an animal crossing the screen and
     * visibly wrong for one striking down it. The Storm Serpent's ring is drawn with no up.
     */
    public Sprite segmentSprite() {
        return boss() == Boss.STORM_SERPENT ? Sprite.STORM_SEGMENT : Sprite.WORM_SEGMENT;
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
