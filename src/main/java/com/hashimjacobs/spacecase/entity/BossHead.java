package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.BossArt;

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
     *
     * Spread across this arc rather than listed, so a boss can field any number of heads. The three
     * hardcoded sectors this replaces gave head four the same arc as head one, which put two heads
     * on top of each other -- the exact failure the sectors exist to prevent.
     */
    private static final double SECTOR_SPAN = 1.40;

    /** How far a head sweeps inside its own sector. */
    private static final double SWING = 0.18;

    /**
     * Ticks for one full sweep. Still deliberate rather than twitchy, at a bit over two seconds.
     *
     * Was 190. SWING, SECTOR_SPAN, REACH_MIN, REACH_MAX and NECK_LENGTH_STEP are all untouched, so
     * a head traces exactly the arc it always did and only travels it faster -- which is why the
     * bounds HydraTest and FrozenEmpressTest hold the necks to are unaffected by this. What does
     * change is which points of that arc land on integer ticks, so the no-two-heads-overlap
     * property is worth re-running rather than reasoning about.
     */
    private static final double SWEEP_TICKS = 140;

    /**
     * Where necks leave the torso: spread across this much of its width, centred.
     *
     * Coupled to the art. The generator draws a socket per head at these positions, so a torso
     * drawn with three sockets and flown with four grows a neck out of blank hide.
     */
    private static final double SOCKET_SPAN = 0.40;
    private static final double SOCKET_DEPTH = 0.78;

    private final EnemyShip body;
    private final int index;
    private final int heads;

    /**
     * Fixed-step age, deliberately not the world tick.
     *
     * {@code Entity.update()} takes no arguments, so reaching the world clock would mean changing
     * that signature everywhere. This is just as frame-rate independent -- {@code World.update()}
     * only runs from the fixed-step part of the loop -- and it is better in one way: the world
     * tick keeps counting through the victory lap and the debrief, when the heads are frozen.
     */
    private int age;

    /** Where a lunge aims: the angle to the nearest pilot, held inside this neck's reach of arc. */
    private double aim;

    BossHead(EnemyShip body, int index, int heads, int health) {
        super(body.boss(), body.centerX(), body.y(), body.scale(), SIZE, SIZE, health,
                Math.max(1, body.boss().scoreValue() / 6));
        this.body = body;
        this.index = index;
        this.heads = Math.max(1, heads);
        // Evenly spaced round the cycle, so the necks never line up and the volleys interleave.
        stagger(index * 9, index * 70);
    }

    @Override
    public boolean isBossPart() {
        return true;
    }

    @Override
    public int siblingParts() {
        return body.parts().size();
    }

    /**
     * The torso does the tracking; a head only takes aim, for its next lunge.
     *
     * Called every fixed step with the nearest pilot ({@code Replayer.driveEnemies}), so a lunge is
     * a pure function of the world state and stays deterministic across lockstep peers.
     */
    @Override
    public void trackAcross(Entity target) {
        Orientation facing = body.orientation();
        double dx = target.centerX() - neckRootX(), dy = target.centerY() - neckRootY();
        double toTarget = StrictMath.atan2(facing.across(dx, dy), Math.max(1, facing.along(dx, dy)));
        double half = SECTOR_SPAN * 0.5 + SWING;
        aim = Math.max(-half, Math.min(half, toTarget));
    }

    /**
     * How far into a lunge this head is: 0 at rest, 1 at full stretch.
     *
     * The worm's strike envelope (BurrowingWorm) squeezed into a third of the cycle: a fast strike,
     * a beat with the jaws out, a slower pull back. The rest of the cycle is the ordinary sweep.
     * Heads take turns, a third of a cycle apart, so one is usually coming at you.
     */
    public double lunge() {
        // The Frozen Empress's heads keep their stately sweep; only the hydra bites.
        if (body.boss() != Boss.HYDRA) {
            return 0;
        }
        int cycle = GameConfig.HYDRA_LUNGE_CYCLE_TICKS;
        double p = Math.floorMod(age + index * cycle / heads, cycle) / (double) cycle;
        if (p >= 0.35) {
            return 0;
        }
        return p < 0.08 ? smooth(p / 0.08) : p < 0.18 ? 1 : 1 - smooth((p - 0.18) / 0.17);
    }

    public boolean lunging() {
        return lunge() > 0.5;
    }

    /** Jaws open while the neck is out, if the art has them. */
    @Override
    public BossArt.State artState() {
        return lunging() && bossArt().has(BossArt.State.BITE) ? BossArt.State.BITE : super.artState();
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
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
        double across = (socket() - 0.5) * facing.acrossExtent(body.width(), body.height());
        double along = (SOCKET_DEPTH - 0.5) * facing.alongExtent(body.width(), body.height());
        return body.centerX() + facing.vx(along, across);
    }

    public double neckRootY() {
        Orientation facing = body.orientation();
        double across = (socket() - 0.5) * facing.acrossExtent(body.width(), body.height());
        double along = (SOCKET_DEPTH - 0.5) * facing.alongExtent(body.width(), body.height());
        return body.centerY() + facing.vy(along, across);
    }

    private double headX() {
        Orientation facing = body.orientation();
        return neckRootX() + facing.vx(StrictMath.cos(angle()) * reach(), StrictMath.sin(angle()) * reach());
    }

    private double headY() {
        Orientation facing = body.orientation();
        return neckRootY() + facing.vy(StrictMath.cos(angle()) * reach(), StrictMath.sin(angle()) * reach());
    }

    /**
     * Where this head sits along the spread, from 0 at one end to 1 at the other.
     *
     * A lone head sits in the middle rather than at one edge, which is why this is not just
     * {@code index / (heads - 1)}.
     */
    private double spread() {
        return heads == 1 ? 0.5 : index / (double) (heads - 1);
    }

    /** Which of the torso's sockets this neck grows from. */
    private double socket() {
        return 0.5 + SOCKET_SPAN * (spread() - 0.5);
    }

    /** An equal share of the cycle between heads, so they are always somewhere different. */
    private double phaseAngle() {
        return 2 * Math.PI * (age / SWEEP_TICKS + index / (double) heads);
    }

    /** Where this neck is pointing, measured from straight down-arena. */
    public double angle() {
        double sector = SECTOR_SPAN * (spread() - 0.5);
        double swept = sector + StrictMath.sin(phaseAngle()) * SWING;
        double lunge = lunge();
        return swept + (aim - swept) * lunge;
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
        double swing = (REACH_MAX - REACH_MIN) * (0.5 + 0.5 * StrictMath.cos(phaseAngle() * 2));
        return REACH_MIN + index * NECK_LENGTH_STEP + swing + lunge() * GameConfig.HYDRA_LUNGE_REACH;
    }
}
