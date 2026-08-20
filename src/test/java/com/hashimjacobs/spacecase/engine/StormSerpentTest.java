package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BurrowingWorm;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Storm Serpent: the same {@link BurrowingWorm} as the Dune Leviathan, on a level that runs the
 * other way.
 *
 * That reuse is the claim Phase 3 was planned on, and it rests on the class holding no orientation
 * of its own -- so the thing worth pinning is that the strike really does turn with the level. There
 * was no test for the top-down path at all before this one: {@code BurrowingWormTest} is hardwired
 * to {@code RIGHT_TO_LEFT}, so nothing proved the axis was derived rather than merely named.
 *
 * The second thing pinned here is the reach, which the two do <em>not</em> share, and the arithmetic
 * for why is in {@code BurrowingWorm.SERPENT_REACH}.
 */
class StormSerpentTest {

    /** Where the player starts, as {@code World} places it: 130 short of the back wall. */
    private static final double PLAYER_LINE = 130;

    private static World arena(Orientation facing) {
        World world = new World(GameMode.SOLO);
        world.setOrientation(facing);
        return world;
    }

    private static BurrowingWorm worm(World world, Boss flagship, Orientation facing) {
        BurrowingWorm worm = new BurrowingWorm(flagship, facing.atX(0, 400, 320, 240),
                facing.atY(0, 400, 320, 240), 1);
        world.addEnemy(worm);
        return worm;
    }

    /** Deepest the leading edge of the maw gets, along whichever axis the level runs on. */
    private static double deepestLeadingEdge(World world, BurrowingWorm worm, Orientation facing) {
        double deepest = 0;
        for (int tick = 0; tick < 800; tick++) {
            world.update();
            double depth = facing.depth(worm.x(), worm.y(), worm.width(), worm.height());
            deepest = Math.max(deepest, depth + facing.alongExtent(worm.width(), worm.height()));
        }
        return deepest;
    }

    /**
     * The load-bearing one: the lunge lands on whichever axis the level runs on.
     *
     * Pinned per axis rather than by comparing the two, because the drift is not the smaller motion
     * and an earlier version of this test wrongly assumed it was. Across is 0.30 of the arena's
     * breadth either way, which on a top-down level is 0.6 x 996 = 598 of travel against a 368-pixel
     * lunge -- so the serpent weaves wider than it strikes deep. That is legitimate, and it gives
     * the player somewhere to be; it just is not what "the long axis" means.
     *
     * What makes this exact is that the two motions are on separate axes. Top-down, only the lunge
     * touches y and only the drift touches x, so a y swing of 368 is the reach and nothing else:
     * 864 x 0.53 = 457.9, less the 90 the burrow mouth sits at.
     */
    @Test
    void theLungeLandsOnTheAxisTheLevelRunsOn() {
        World topDown = arena(Orientation.TOP_DOWN);
        BurrowingWorm serpent = worm(topDown, Boss.STORM_SERPENT, Orientation.TOP_DOWN);

        double lowY = Double.MAX_VALUE;
        double highY = -Double.MAX_VALUE;
        for (int tick = 0; tick < 800; tick++) {
            topDown.update();
            lowY = Math.min(lowY, serpent.y());
            highY = Math.max(highY, serpent.y());
        }

        assertTrue(Math.abs((highY - lowY) - 367.92) < 3,
                "a top-down lunge is 864 x 0.53 less the 90 burrow depth, so 367.9; measured "
                        + (highY - lowY));
    }

    /** And the level 9 fight still runs on the other axis, from the same class. */
    @Test
    void theLeviathanStillStrikesAcross() {
        World sideOn = arena(Orientation.RIGHT_TO_LEFT);
        BurrowingWorm leviathan =
                worm(sideOn, Boss.DUNE_LEVIATHAN, Orientation.RIGHT_TO_LEFT);

        double lowX = Double.MAX_VALUE;
        double highX = -Double.MAX_VALUE;
        double lowY = Double.MAX_VALUE;
        double highY = -Double.MAX_VALUE;
        for (int tick = 0; tick < 800; tick++) {
            sideOn.update();
            lowX = Math.min(lowX, leviathan.x());
            highX = Math.max(highX, leviathan.x());
            lowY = Math.min(lowY, leviathan.y());
            highY = Math.max(highY, leviathan.y());
        }

        assertTrue(Math.abs((highX - lowX) - 527.52) < 3,
                "level 9's lunge is 996 x 0.62 less the 90 burrow depth, so 527.5; measured "
                        + (highX - lowX));
        assertTrue(highY - lowY > 400, "and it should still be drifting across the lane, not held");
    }

    /**
     * Neither maw may finish its lunge past the line the player starts on.
     *
     * This is what the serpent's own reach buys, and the reason it could not simply inherit the
     * Leviathan's 0.62: what matters is the reach plus the art's extent along the strike axis, and
     * both of those differ between a 996-deep side-on arena and an 864-deep top-down one.
     */
    @Test
    void neitherMawReachesTheLineThePlayerStartsOn() {
        World topDown = arena(Orientation.TOP_DOWN);
        double serpent = deepestLeadingEdge(topDown,
                worm(topDown, Boss.STORM_SERPENT, Orientation.TOP_DOWN), Orientation.TOP_DOWN);
        double serpentLine = Orientation.TOP_DOWN.arenaDepth() - PLAYER_LINE;
        assertTrue(serpent < serpentLine,
                "the serpent's jaws finished at " + serpent + ", past the player's " + serpentLine);

        World sideOn = arena(Orientation.RIGHT_TO_LEFT);
        double leviathan = deepestLeadingEdge(sideOn,
                worm(sideOn, Boss.DUNE_LEVIATHAN, Orientation.RIGHT_TO_LEFT),
                Orientation.RIGHT_TO_LEFT);
        double leviathanLine = Orientation.RIGHT_TO_LEFT.arenaDepth() - PLAYER_LINE;
        assertTrue(leviathan < leviathanLine,
                "the leviathan's jaws finished at " + leviathan + ", past the player's "
                        + leviathanLine);
    }

    /**
     * Level 9 has shipped, so its reach is not something a later galaxy may retune.
     *
     * Pinned as an absolute rather than as a fraction, because the fraction is exactly the thing
     * that would be tempting to edit: 996 x 0.62 is 617.5, and the maw is 214 along that axis.
     */
    @Test
    void theLeviathansReachIsWhatItWasTunedAt() {
        World sideOn = arena(Orientation.RIGHT_TO_LEFT);
        double deepest = deepestLeadingEdge(sideOn,
                worm(sideOn, Boss.DUNE_LEVIATHAN, Orientation.RIGHT_TO_LEFT),
                Orientation.RIGHT_TO_LEFT);

        assertTrue(Math.abs(deepest - 831.5) < 1.5,
                "level 9's strike used to finish at 831.5 and now finishes at " + deepest);
    }

    /**
     * The two trail different rings, which is a picture problem rather than a numbers one.
     *
     * The Leviathan's ring carries its bristles down one side. That is right for an animal crossing
     * the screen and visibly wrong for one striking down it, and no assertion about position would
     * ever have noticed.
     */
    @Test
    void eachWormTrailsItsOwnRings() {
        World topDown = arena(Orientation.TOP_DOWN);
        World sideOn = arena(Orientation.RIGHT_TO_LEFT);

        Sprite serpentRing =
                worm(topDown, Boss.STORM_SERPENT, Orientation.TOP_DOWN).segmentSprite();
        Sprite leviathanRing =
                worm(sideOn, Boss.DUNE_LEVIATHAN, Orientation.RIGHT_TO_LEFT).segmentSprite();

        assertSame(Sprite.STORM_SEGMENT, serpentRing);
        assertSame(Sprite.WORM_SEGMENT, leviathanRing);
        assertNotEquals(serpentRing, leviathanRing, "one ring cannot be right for both axes");
    }
}
