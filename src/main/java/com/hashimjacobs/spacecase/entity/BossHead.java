package com.hashimjacobs.spacecase.entity;

/**
 * One of the hydra's heads: a target in its own right, on a neck that never stops moving.
 *
 * A head is an ordinary entity in the world's enemy list, which is the whole trick. Collision,
 * the sweep, explosions, scoring and rendering all already know what to do with one, so making
 * the heads separately shootable costs a movement rule and four small branches rather than a
 * second collision system. Its box <em>is</em> its position -- written once per fixed step and
 * read by everything else -- so there is no hitbox to keep in sync with the art.
 */
public final class BossHead extends EnemyShip {

    /** Small enough that hitting one takes aim, big enough to hit while it is swinging. */
    private static final double SIZE = 64;

    /** How far a neck stretches at full extension, and how tightly it coils back. */
    private static final double REACH_MAX = 210;
    private static final double REACH_MIN = 90;

    /**
     * Extra length per head, so no two ever occupy the same point.
     *
     * Kept small enough that the longest neck at full stretch still leaves the head inside the
     * arena: 90 + 2 x 34 + 120 = 278 from a torso holding station 90 in, against an 864 arena.
     */
    private static final double NECK_LENGTH_STEP = 34;

    /**
     * Each neck keeps to its own arc, and only sweeps within it.
     *
     * Phase offsets alone were not enough to keep the heads apart -- three phases a third of a
     * cycle apart still hand two of them the same sine twice per cycle, and they stacked exactly
     * on top of each other. Sectors make the separation structural instead of hoping the maths
     * misses, and they have a second benefit: you can tell which head is which, which matters
     * when the point of the fight is shooting them off one at a time.
     *
     * Zero is down-arena, following the {@code EnemyWeapons} convention rather than Rocket's.
     */
    private static final double[] SECTORS = {-0.70, 0, 0.70};

    /** How far a head sweeps inside its own sector. */
    private static final double SWING = 0.18;

    /** Ticks for one full sweep. Long enough to read as deliberate rather than twitchy. */
    private static final double SWEEP_TICKS = 190;

    /** Where a neck leaves the torso. Must match NECK_SOCKETS in tools/GenerateAssets. */
    private static final double[] SOCKETS = {0.30, 0.50, 0.70};
    private static final double SOCKET_DEPTH = 0.78;

    private final EnemyShip body;
    private final int index;

    /**
     * Fixed-step age, deliberately not the world tick.
     *
     * {@code Entity.update()} takes no arguments, so reaching the world clock would mean changing
     * that signature everywhere. This is just as frame-rate independent -- {@code World.update()}
     * only runs from the fixed-step part of the loop -- and it is better in one way: the world
     * tick keeps counting through the victory lap and the debrief, when the heads are frozen.
     */
    private int age;

    BossHead(EnemyShip body, int index, int heads, int health) {
        super(body.boss(), body.centerX(), body.y(), body.scale(), SIZE, SIZE, health,
                Math.max(1, body.boss().scoreValue() / 6));
        this.body = body;
        this.index = index;
        // Thirds of a cycle apart, so the three necks never line up and the volleys interleave.
        stagger(index * 9, index * 70);
    }

    @Override
    public boolean isBossPart() {
        return true;
    }

    /** The torso does the tracking; a head goes where its neck takes it. */
    @Override
    public void trackAcross(Entity target) {
    }

    /**
     * A head fights on its own health, not the flagship's.
     *
     * Wounded, it stops spraying and starts picking -- so shooting a head makes it briefly more
     * dangerous, which is the reason to finish one rather than spread damage across all three.
     */
    @Override
    public BossPhase phase() {
        return remainingHealthFraction() > 0.5 ? BossPhase.SPREAD : BossPhase.AIMED_BURST;
    }

    /**
     * Deliberately does not call {@code super.update()}.
     *
     * The inherited station-keeping clamp would haul every head back to the flagship's holding
     * depth the moment a neck swung past it, which is exactly the movement this class exists for.
     */
    @Override
    public void update() {
        if (!body.isAlive()) {
            // No head outlives its torso, whichever order they happen to die in.
            kill();
            return;
        }
        age++;
        setPosition(headX() - width() / 2, headY() - height() / 2);
    }

    /** Where this head's neck leaves the body, in world coordinates. */
    public double neckRootX() {
        Orientation facing = body.orientation();
        double across = (SOCKETS[index % SOCKETS.length] - 0.5)
                * facing.acrossExtent(body.width(), body.height());
        double along = (SOCKET_DEPTH - 0.5) * facing.alongExtent(body.width(), body.height());
        return body.centerX() + facing.vx(along, across);
    }

    public double neckRootY() {
        Orientation facing = body.orientation();
        double across = (SOCKETS[index % SOCKETS.length] - 0.5)
                * facing.acrossExtent(body.width(), body.height());
        double along = (SOCKET_DEPTH - 0.5) * facing.alongExtent(body.width(), body.height());
        return body.centerY() + facing.vy(along, across);
    }

    private double headX() {
        Orientation facing = body.orientation();
        return neckRootX() + facing.vx(Math.cos(angle()) * reach(), Math.sin(angle()) * reach());
    }

    private double headY() {
        Orientation facing = body.orientation();
        return neckRootY() + facing.vy(Math.cos(angle()) * reach(), Math.sin(angle()) * reach());
    }

    /** A third of a cycle between heads, so all three are always somewhere different. */
    private double phaseAngle() {
        return 2 * Math.PI * (age / SWEEP_TICKS + index / (double) SOCKETS.length);
    }

    /** Where this neck is pointing, measured from straight down-arena. */
    public double angle() {
        return SECTORS[index % SECTORS.length] + Math.sin(phaseAngle()) * SWING;
    }

    /**
     * How far the neck is extended.
     *
     * Twice the sweep frequency, so a head stabs forward at each end of its arc instead of gliding
     * round at a constant distance. That is what makes the movement read as hunting.
     *
     * Each neck is also a fixed amount longer than the last. Phase offsets alone are not enough to
     * keep the heads apart: three phases a third of a cycle apart still give two of them the same
     * sine twice per cycle, and when that happened the heads sat exactly on top of each other.
     * Different lengths mean they can share an angle without sharing a position.
     */
    private double reach() {
        double swing = (REACH_MAX - REACH_MIN) * (0.5 + 0.5 * Math.cos(phaseAngle() * 2));
        return REACH_MIN + index * NECK_LENGTH_STEP + swing;
    }
}
