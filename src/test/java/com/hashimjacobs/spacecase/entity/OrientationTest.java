package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole point of this class is that TOP_DOWN is the identity, so every call site rewritten
 * through it keeps its old value. That property is asserted directly here; if it ever breaks,
 * every vertical level in the game breaks with it.
 */
class OrientationTest {

    private static final double EPSILON = 1e-9;

    @Test
    void topDownIsTheIdentity() {
        Orientation o = Orientation.TOP_DOWN;

        assertEquals(7, o.along(3, 7), EPSILON);
        assertEquals(3, o.across(3, 7), EPSILON);
        assertEquals(3, o.vx(7, 3), EPSILON);
        assertEquals(7, o.vy(7, 3), EPSILON);
        assertEquals(64, o.alongExtent(60, 64), EPSILON);
        assertEquals(60, o.acrossExtent(60, 64), EPSILON);
        assertEquals(200, o.depth(100, 200, 60, 64), EPSILON);
        assertEquals(100, o.atX(200, 100, 60, 64), EPSILON);
        assertEquals(200, o.atY(200, 100, 60, 64), EPSILON);
        assertEquals(GameConfig.HEIGHT, o.arenaDepth(), EPSILON);
        assertEquals(GameConfig.WIDTH, o.arenaBreadth(), EPSILON);
        assertEquals(Facing.UP, o.playerFacing());
    }

    /** Projecting a vector out and back must return it, or every firing angle drifts. */
    @Test
    void vectorsRoundTripThroughBothProjections() {
        for (Orientation o : Orientation.values()) {
            for (double along : new double[]{-5, 0, 3.5, 10}) {
                for (double across : new double[]{-4, 0, 2.25, 8}) {
                    double x = o.vx(along, across);
                    double y = o.vy(along, across);
                    assertEquals(along, o.along(x, y), EPSILON, o + " lost the along component");
                    assertEquals(across, o.across(x, y), EPSILON, o + " lost the across component");
                }
            }
        }
    }

    /** Same for positions, which travel through the other pair of methods. */
    @Test
    void positionsRoundTripThroughDepthAndBack() {
        double w = 60;
        double h = 64;
        for (Orientation o : Orientation.values()) {
            for (double depth : new double[]{-190, 0, 90, 500}) {
                for (double across : new double[]{0, 120, 700}) {
                    double x = o.atX(depth, across, w, h);
                    double y = o.atY(depth, across, w, h);
                    assertEquals(depth, o.depth(x, y, w, h), EPSILON, o + " lost the depth");
                    assertEquals(across, o.across(x, y), EPSILON, o + " lost the lane");
                }
            }
        }
    }

    @Test
    void aSideViewLevelRunsTowardTheLeftWall() {
        Orientation o = Orientation.RIGHT_TO_LEFT;

        assertTrue(o.vx(1, 0) < 0, "advancing down-arena should move left");
        assertEquals(0, o.vy(1, 0), EPSILON, "advancing should not move vertically");
        assertEquals(Facing.RIGHT, o.playerFacing());
    }

    /** A box entering from the right starts at negative depth, the same as one entering the top. */
    @Test
    void somethingStillOffTheEntryEdgeHasNegativeDepth() {
        double w = 226;
        double h = 168;

        assertTrue(Orientation.TOP_DOWN.depth(400, -h, w, h) < 0);
        assertTrue(Orientation.RIGHT_TO_LEFT.depth(GameConfig.WIDTH, 400, w, h) < 0);
    }

    @Test
    void theTwoOrientationsSwapTheExtents() {
        assertEquals(Orientation.TOP_DOWN.alongExtent(60, 64),
                Orientation.RIGHT_TO_LEFT.acrossExtent(60, 64), EPSILON);
        assertEquals(Orientation.TOP_DOWN.acrossExtent(60, 64),
                Orientation.RIGHT_TO_LEFT.alongExtent(60, 64), EPSILON);
        assertEquals(Orientation.TOP_DOWN.arenaDepth(),
                Orientation.RIGHT_TO_LEFT.arenaBreadth(), EPSILON);
    }

    @Test
    void everyFacingHasAnOppositeOnItsOwnAxis() {
        for (Facing facing : Facing.values()) {
            Facing back = facing.opposite();
            assertEquals(facing, back.opposite(), "opposite must be its own inverse");
            assertEquals(-facing.xDirection(), back.xDirection());
            assertEquals(-facing.yDirection(), back.yDirection());
        }
    }

    @Test
    void theHorizontalFacingsPointSidewaysAndAreRotatedAQuarterTurn() {
        assertEquals(1, Facing.RIGHT.xDirection());
        assertEquals(0, Facing.RIGHT.yDirection());
        assertEquals(90, Facing.RIGHT.rotationDegrees(), EPSILON);
        assertEquals(-1, Facing.LEFT.xDirection());
        assertEquals(270, Facing.LEFT.rotationDegrees(), EPSILON);
        // The vertical pair must not have shifted while the horizontal pair was added.
        assertEquals(0, Facing.UP.rotationDegrees(), EPSILON);
        assertEquals(180, Facing.DOWN.rotationDegrees(), EPSILON);
        assertEquals(-1, Facing.UP.yDirection());
    }
}
