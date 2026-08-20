package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.WorldTemplate;

/**
 * Rock closing in from both sides of the lane, scrolling with the level.
 *
 * The game had no static geometry before this: everything was one box against another, and the
 * tunnel walls in the cavern levels were painted into the backdrop and explicitly non-interactive.
 * This makes them real without adding a second collision system.
 *
 * <p><strong>The surface is a heightfield on a fixed lattice, and the renderer's polygon vertices
 * are those same lattice nodes.</strong> So the line drawn on screen <em>is</em> the line collision
 * uses -- there is no tolerance to tune, no "the ship stops a few pixels short of the rock", and no
 * "the nose is visibly inside it". That property is the reason this is a heightfield rather than a
 * column of asteroid entities: a single-valued surface has no interior for a ship to get wedged in,
 * where a row of boxes has a gap between every pair.
 *
 * <p>Two things make it fair by construction rather than by tuning. The inset is derived from the
 * template's guaranteed lane, so <em>the lane cannot close below that width whatever the roughness
 * is</em> -- an unwinnable pinch is arithmetically impossible, not merely unlikely. And the tunnel
 * opens into a chamber when the flagship arrives, because a four-hundred-pixel boss in a
 * five-hundred-pixel lane is not a fight.
 *
 * <p>No JavaFX, so the arithmetic is testable on a headless runner. Nothing here is drawn; the
 * renderer reads the same numbers.
 */
public final class Terrain {

    /**
     * Lattice pitch in pixels, shared by collision and the renderer.
     *
     * Load-bearing that there is one constant. The drawn edge and the collided edge agree because
     * they are sampled at the same points; reading this in one place and hard-coding it in the other
     * would put the visible surface a fraction off the real one, which is the single failure mode
     * that would make this feel broken rather than hard.
     */
    public static final double PITCH = 9;

    /** Screens of scroll before the rock repeats. Four, so the loop is not obvious in a wave. */
    private static final int PERIOD_SCREENS = 4;

    /** Ticks the chamber takes to open once the flagship shows up. About three seconds. */
    private static final int OPEN_TICKS = 180;

    /**
     * How hard the wobble is pushed toward its extremes, forming ridges.
     *
     * A plain sum of harmonics is smooth: it undulates, and it reads as a corridor that narrows
     * rather than as rock. Pushing it toward its extremes flattens the open stretches and steepens
     * the walls, which turns gentle waves into lobes with throats between them -- rock that
     * protrudes into the lane, which is the thing you are supposed to be avoiding.
     *
     * Done with tanh rather than a fractional power, and the reason is worth recording. Raising the
     * wobble to {@code 1/3} does push it outward, but its slope at zero is infinite, so it puts a
     * cusp exactly where the wobble crosses the middle of its range. That measured as a 56-pixel
     * step across a 9-pixel lattice -- a wall face moving sideways faster than the ship can fly,
     * which shoves a pilot along it rather than letting them fly off it. tanh reaches the same
     * extremes with its steepest slope bounded by this gain.
     *
     * Safe for the lane guarantee either way: the weights sum to one so the wobble is already inside
     * plus or minus one, and {@code tanh(g x) / tanh(g)} maps that range onto itself. The most the
     * rock can reach is still exactly half the spare width per side.
     *
     * One constant rather than a per-template knob. No level has wanted its own value; promote it to
     * {@link WorldTemplate} the first time one does, rather than carrying a column nothing varies.
     */
    private static final double RIDGE_GAIN = 2.5;

    /** Matches the near parallax layer, so rock and foreground move together. */
    private static final double SCROLL_PER_TICK =
            com.hashimjacobs.spacecase.GameConfig.BACKGROUND_SCROLL_SPEED * 1.5;

    /** The default: open space, and one boolean per tick. */
    public static final Terrain NONE =
            new Terrain(WorldTemplate.OPEN_FIELD, Orientation.TOP_DOWN, 0);

    private final WorldTemplate template;
    private final Orientation facing;

    /** Rock reaching in from each side, indexed by lattice node. Empty for an open field. */
    private final double[] low;
    private final double[] high;

    private int tick;

    /** 0 while the tunnel is closed, 1 once the boss chamber is fully open. Never goes back. */
    private double openness;
    private boolean opening;

    public Terrain(WorldTemplate template, Orientation facing, long seed) {
        this.template = template;
        this.facing = facing;
        if (!template.hasRock()) {
            this.low = new double[0];
            this.high = new double[0];
            return;
        }
        int nodes = Math.max(2, (int) Math.round(PERIOD_SCREENS * facing.arenaDepth() / PITCH));
        // Its own Random, never the spawn director's. Drawing from that one would shift every
        // subsequent spawn and break the fixed-seed run the director's tests pin.
        Random shape = new Random(seed);
        this.low = profile(nodes, shape);
        this.high = profile(nodes, shape);
    }

    /**
     * One side's rock, as a sum of harmonics that closes on itself.
     *
     * Harmonic {@code k} runs {@code k} whole cycles across the table, so the last node joins the
     * first without a step -- the same trick the backdrop layers use to tile. Weights fall off
     * slowly and are normalised to sum to one, so the wobble stays within plus or minus one however
     * many terms there are, which is what keeps the squeeze below exact.
     *
     * The decay is what decides whether this reads as rock. Falling off as {@code 1/(k+1)} leaves
     * the fundamental dominant and the result is a smooth taper; a slower decay keeps enough of the
     * higher harmonics to put detail on the walls. Combined with {@link #RIDGE_GAIN} that is
     * the difference between a corridor and a cave.
     */
    private double[] profile(int nodes, Random shape) {
        int harmonics = Math.max(1, template.harmonics());
        double[] weight = new double[harmonics];
        double[] phase = new double[harmonics];
        double total = 0;
        for (int k = 0; k < harmonics; k++) {
            weight[k] = 1.0 / Math.pow(k + 1, 0.8);
            total += weight[k];
            phase[k] = shape.nextDouble() * 2 * Math.PI;
        }
        for (int k = 0; k < harmonics; k++) {
            weight[k] /= total;
        }

        // Half the arena's spare width, shared between the two sides, divided by the most the
        // wobble can add. Whatever the harmonics do, low + high never exceeds the spare width, so
        // the lane never drops below minLaneFraction.
        double spare = facing.arenaBreadth() * (1 - template.minLaneFraction());
        double squeeze = spare / 2 / (1 + template.roughness());

        double[] table = new double[nodes];
        for (int i = 0; i < nodes; i++) {
            double wobble = 0;
            for (int k = 0; k < harmonics; k++) {
                wobble += weight[k] * Math.sin(2 * Math.PI * (k + 1) * i / (double) nodes + phase[k]);
            }
            double ridged = Math.tanh(RIDGE_GAIN * wobble) / Math.tanh(RIDGE_GAIN);
            table[i] = squeeze * (1 + template.roughness() * ridged);
        }
        return table;
    }

    /**
     * Advances the scroll clock, and opens the chamber once the flagship is here.
     *
     * The latch only ever opens. That is not politeness: the victory lap ticks scenery but does not
     * run collision, so rock closing back in during it would put the ships inside a wall with
     * nothing to push them out.
     */
    public void tick(boolean bossPresent) {
        tick++;
        if (bossPresent) {
            opening = true;
        }
        if (opening && openness < 1) {
            openness = Math.min(1, openness + 1.0 / OPEN_TICKS);
        }
    }

    /** Rock reaching in from one side at a screen depth. {@code side} -1 is low across, +1 high. */
    public double inset(double depth, int side) {
        if (isEmpty()) {
            return 0;
        }
        double[] table = side < 0 ? low : high;
        double at = (depth + tick * SCROLL_PER_TICK) / PITCH;
        int node = Math.floorMod((int) Math.floor(at), table.length);
        double between = at - Math.floor(at);
        double here = table[node];
        double next = table[(node + 1) % table.length];
        return (here + between * (next - here)) * (1 - openness);
    }

    /** The near edge of the open lane, in across coordinates. */
    public double laneLow(double depth) {
        return inset(depth, -1);
    }

    /** The far edge of the open lane, in across coordinates. */
    public double laneHigh(double depth) {
        return facing.arenaBreadth() - inset(depth, 1);
    }

    /**
     * Shoves a box back inside the lane, by the shorter way out. True when it actually moved.
     *
     * Samples every lattice node the box spans plus both its ends, and takes the worst of them, so a
     * long ship cannot bridge a spike. That is at most a dozen array lookups.
     */
    public boolean pushInside(Entity entity) {
        if (isEmpty()) {
            return false;
        }
        double depth = facing.depth(entity.x(), entity.y(), entity.width(), entity.height());
        double alongExtent = facing.alongExtent(entity.width(), entity.height());
        double acrossExtent = facing.acrossExtent(entity.width(), entity.height());

        double worstLow = Double.NEGATIVE_INFINITY;
        double worstHigh = Double.POSITIVE_INFINITY;
        for (double at = depth; ; at += PITCH) {
            double sampleAt = Math.min(at, depth + alongExtent);
            worstLow = Math.max(worstLow, laneLow(sampleAt));
            worstHigh = Math.min(worstHigh, laneHigh(sampleAt));
            if (sampleAt >= depth + alongExtent) {
                break;
            }
        }

        double across = facing.across(entity.x(), entity.y());
        double room = worstHigh - acrossExtent;
        // When the rock is tighter than the ship is wide there is no correct answer, so centre it
        // rather than picking a side. Cannot happen with the squeeze above, but a template edited
        // to an impossible lane should wedge the ship in the middle, not teleport it to an edge.
        double clamped = room < worstLow
                ? (worstLow + room) / 2
                : Math.max(worstLow, Math.min(room, across));
        if (Math.abs(clamped - across) < 1e-9) {
            return false;
        }
        entity.setPosition(facing.atX(depth, clamped, entity.width(), entity.height()),
                facing.atY(depth, clamped, entity.width(), entity.height()));
        return true;
    }

    /** Whether a point is inside rock. What a bullet and a drifting asteroid ask. */
    public boolean solidAt(double x, double y) {
        if (isEmpty()) {
            return false;
        }
        double depth = facing.depth(x, y, 0, 0);
        double across = facing.across(x, y);
        return across < laneLow(depth) || across > laneHigh(depth);
    }

    /** True when there is no rock, which is every level but the tunnels. */
    public boolean isEmpty() {
        return low.length == 0;
    }

    public WorldTemplate template() {
        return template;
    }

    public Orientation orientation() {
        return facing;
    }
}
