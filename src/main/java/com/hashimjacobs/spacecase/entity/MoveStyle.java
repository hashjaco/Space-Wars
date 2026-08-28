package com.hashimjacobs.spacecase.entity;

/**
 * How one ship crosses the arena, as arithmetic rather than as a subclass.
 *
 * Every style is a pure function of a tick count and the offset to the target -- the discipline
 * {@link BurrowingWorm}, {@link PilotedMech} and {@link VoidEntity} all keep, for the same two
 * reasons: two ships built alike stay alike to the last bit, and there is no state to restore when
 * a fight is paused or a run is resumed.
 *
 * <strong>Nothing here touches x or y.</strong> Everything is in {@link Orientation}'s terms --
 * <em>along</em> the direction of travel and <em>across</em> the lane -- and
 * {@link EnemyShip#trackAcross} does the projection. That is what makes every style below work
 * unchanged on the six side-on levels without a single branch on which way the level runs.
 *
 * {@link #HUNT} is the identity: it reproduces exactly what every enemy in the game did before this
 * enum existed, which is what lets the existing suite stand as the regression test for it.
 *
 * This exists because authored positions are worthless without it. {@code GameLoop.driveEnemies}
 * calls {@link EnemyShip#trackAcross} on every enemy every tick and it rewrites the across
 * component from scratch, so a six-ship wedge converges onto the player inside a second and the
 * formation somebody wrote is gone. {@link #STRAIGHT} is the answer to that, and the rest of the
 * enum is what became cheap once one style existed.
 */
public enum MoveStyle {

    /**
     * Closes across the lane while advancing down it, jinking by whatever its archetype jinks.
     *
     * The default, and deliberately the identity: the sine term is
     * {@code EnemyShip.swerve()} moved here unchanged, and the advance is the carried one.
     */
    HUNT {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return Math.signum(toTarget) * trackSpeed + Math.sin(phase(clock)) * weave;
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            return carried;
        }
    },

    /**
     * Flies the lane it entered by and never turns.
     *
     * The one style that makes a formation possible at all -- a wing of these stays the shape it
     * was authored in, because nothing pulls it toward the player. Everything else in this enum is
     * a variation somebody wanted once this existed.
     */
    STRAIGHT {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return 0;
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            return carried;
        }
    },

    /**
     * A serpentine ribbon with no closing term at all, so it never converges on anybody.
     *
     * Different in kind from a jinking {@link #HUNT} scout rather than different in degree: this is
     * a lane you can stand beside and wait out, and that is the point of it. The amplitude is held
     * at or above the archetype's own so the sine is the whole motion -- a weave that is smaller
     * than a track speed is a wobble on a straight line, which is the distinction
     * {@code EnemyKind.weave()} already draws.
     */
    WEAVE {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return Math.sin(phase(clock)) * Math.max(weave, HARD_WEAVE);
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            return carried;
        }
    },

    /**
     * Drifts in slowly, then commits.
     *
     * No jink: it is not evading, it has picked somewhere to be. Peaks at twice its archetype's
     * speed, which for the fastest hull in the game is still under {@code GameConfig.PLAYER_SPEED}
     * -- the same ceiling {@code EnemyShip.ENEMY_SPEED_SCALE_CAP} exists to hold, and for the same
     * reason. The counterplay is to move before it commits, which only works if the wind-up is
     * long enough to read; DIVE_TICKS is that reading time.
     */
    DIVE {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return Math.signum(toTarget) * trackSpeed;
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            double committed = Math.min(1, Math.max(0, clock) / (double) DIVE_TICKS);
            return descentSpeed * (DIVE_CREEP + (2 - DIVE_CREEP) * committed);
        }
    },

    /**
     * Advances part of the way in, stops, and strafes to stay above you.
     *
     * The only style that cannot be waited out. Everything else eventually leaves through the far
     * edge and {@code World.killWhatLeftTheArena} takes it; this one parks and has to be killed.
     *
     * That is also its hazard: a parked ship holds one of the difficulty preset's enemy slots
     * indefinitely, so a lane of six of these wedges the arena shut. Author it in ones and twos.
     * Deliberately not fixed with a lifetime timer -- it is a data mistake with an obvious symptom,
     * and a timer would make a ship somebody placed vanish for reasons nobody wrote down.
     */
    HOLD {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return Math.signum(toTarget) * trackSpeed;
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            return progress > HOLD_AT ? 0 : carried;
        }
    },

    /**
     * A slow cosine that reads as a straight diagonal across one crossing of the screen.
     *
     * A quarter of the swerve rate, so a ship completes well under half a cycle on its way through.
     * Two ships spawned apart start at different points of it, so a wing authored as a line fans
     * out as it comes rather than arriving as one rank.
     */
    DRIFT {
        @Override
        double acrossStep(double toTarget, double trackSpeed, double weave, int clock) {
            return Math.cos(phase(clock) * DRIFT_RATE) * trackSpeed;
        }

        @Override
        double advance(double carried, double descentSpeed, double progress, int clock) {
            return carried;
        }
    };

    /**
     * Ticks for one full swerve. Roughly a second and three quarters, which reads as a decision.
     *
     * Was {@code EnemyShip.WEAVE_PERIOD_TICKS}; it lives here now because three styles read it and
     * the ship itself no longer does.
     */
    public static final int PERIOD_TICKS = 105;

    /** Floor on {@link #WEAVE}'s amplitude, above the fastest archetype's track speed. */
    private static final double HARD_WEAVE = 3.2;

    /** How long {@link #DIVE} takes to commit: two and a half seconds of warning. */
    private static final int DIVE_TICKS = 150;

    /** The share of its speed a {@link #DIVE} carries before it commits. */
    private static final double DIVE_CREEP = 0.35;

    /** How far in a {@link #HOLD} advances, as a fraction of the arena's depth. */
    private static final double HOLD_AT = 0.30;

    /** {@link #DRIFT}'s share of the swerve rate. */
    private static final double DRIFT_RATE = 0.25;

    static double phase(int clock) {
        return clock * (2 * Math.PI / PERIOD_TICKS);
    }

    /** This tick's sideways step, in pixels across the lane. Positive is toward increasing across. */
    abstract double acrossStep(double toTarget, double trackSpeed, double weave, int clock);

    /**
     * This tick's advance down-arena.
     *
     * @param carried      the advance already in flight. {@link #HUNT} returns it untouched, which
     *                     is what keeps a flagship parked: {@code EnemyShip.update} zeroes this
     *                     once a boss reaches its holding depth, and recomputing it from
     *                     {@code descentSpeed} here would undo that every tick and walk the boss
     *                     off the bottom of the arena.
     * @param descentSpeed this ship's own down-arena speed, archetype and scaling already applied
     * @param progress     how far into the arena this ship is, 0 at the edge it entered by and 1 at
     *                     the far wall. A fraction, so a style holds the same relative station on
     *                     an 864-deep top-down level and a 996-deep side-on one.
     * @param clock        this ship's age plus its own phase offset
     */
    abstract double advance(double carried, double descentSpeed, double progress, int clock);
}
