package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * Which way a level runs, as arithmetic rather than as a flag.
 *
 * The game began as a single vertical descent, with "down the screen" hard-coded into spawn
 * positions, station-keeping, firing angles, culling and the backdrop scroll. A side-view level
 * needs every one of those turned ninety degrees. Carrying a flag instead of these projections
 * would leave each of those places writing its own {@code if}, which is the scattered branching
 * this exists to prevent -- so the enum carries behaviour and the call sites carry none.
 *
 * Everything is expressed in two axes: <em>along</em>, the direction hostiles travel, and
 * <em>across</em>, the width of the lane they travel down. {@link #TOP_DOWN} is the identity in
 * every method below, which is what makes the conversion safe -- every existing call site becomes
 * a different expression with the same value, so the existing tests are the regression suite.
 *
 * <p><strong>{@link #along} is a delta, {@link #depth} is a position.</strong> The first projects
 * a velocity or an offset onto the direction of travel; the second measures how far a box's
 * trailing edge has come from the edge it entered by. They differ by a translation and by the
 * box's own extent, and confusing them is the mistake to watch for.
 */
public enum Orientation {

    /** The original: hostiles fall from the top and the player holds the bottom. */
    TOP_DOWN(Facing.UP),

    /** Side view: hostiles come in from the right and the player holds the left. */
    RIGHT_TO_LEFT(Facing.RIGHT);

    private final Facing playerFacing;

    Orientation(Facing playerFacing) {
        this.playerFacing = playerFacing;
    }

    /** Which way a player's ship points, and therefore fires, in a level running this way. */
    public Facing playerFacing() {
        return playerFacing;
    }

    /** Component of a delta or velocity along the direction of travel. Positive is down-arena. */
    public double along(double x, double y) {
        return this == TOP_DOWN ? y : -x;
    }

    /** Component of a delta, velocity or position across the lane. */
    public double across(double x, double y) {
        return this == TOP_DOWN ? x : y;
    }

    /** World x of a vector that runs {@code along} down-arena and {@code across} sideways. */
    public double vx(double along, double across) {
        return this == TOP_DOWN ? across : -along;
    }

    public double vy(double along, double across) {
        return this == TOP_DOWN ? along : across;
    }

    /** A box's extent along the direction of travel. */
    public double alongExtent(double width, double height) {
        return this == TOP_DOWN ? height : width;
    }

    /** A box's extent across the lane. */
    public double acrossExtent(double width, double height) {
        return this == TOP_DOWN ? width : height;
    }

    /**
     * How far a box's trailing edge has travelled from the edge it entered by.
     *
     * Negative while a box is still off-screen on the entry side, which is what lets a boss spawn
     * outside the arena and descend into it without being culled on arrival.
     */
    public double depth(double x, double y, double width, double height) {
        return this == TOP_DOWN ? y : GameConfig.WIDTH - x - width;
    }

    /** World x of a box placed at the given depth and across position. Inverse of {@link #depth}. */
    public double atX(double depth, double across, double width, double height) {
        return this == TOP_DOWN ? across : GameConfig.WIDTH - depth - width;
    }

    public double atY(double depth, double across, double width, double height) {
        return this == TOP_DOWN ? depth : across;
    }

    /** How far the arena runs in the direction of travel. */
    public double arenaDepth() {
        return this == TOP_DOWN ? GameConfig.HEIGHT : GameConfig.WIDTH;
    }

    /** How wide the lane is. */
    public double arenaBreadth() {
        return this == TOP_DOWN ? GameConfig.WIDTH : GameConfig.HEIGHT;
    }
}
