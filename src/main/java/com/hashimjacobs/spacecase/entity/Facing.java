package com.hashimjacobs.spacecase.entity;

/**
 * Which way a ship points, and therefore which way its bullets travel.
 *
 * Started as a pair for battle mode, where player two sits at the top of the arena and fires
 * downward. The horizontal pair arrived with side-view levels; see {@link Orientation}.
 */
public enum Facing {

    UP(0, -1, 0),
    DOWN(0, 1, 180),
    RIGHT(1, 0, 90),
    LEFT(-1, 0, 270);

    private final int xDirection;
    private final int yDirection;
    private final double rotationDegrees;

    Facing(int xDirection, int yDirection, double rotationDegrees) {
        this.xDirection = xDirection;
        this.yDirection = yDirection;
        this.rotationDegrees = rotationDegrees;
    }

    /** -1 travels toward the left of the screen, +1 toward the right, 0 for the vertical pair. */
    public int xDirection() {
        return xDirection;
    }

    /** -1 travels toward the top of the screen, +1 toward the bottom, 0 for the horizontal pair. */
    public int yDirection() {
        return yDirection;
    }

    /** Degrees to rotate the sprite so it visually points the right way. */
    public double rotationDegrees() {
        return rotationDegrees;
    }

    /** The other end of this axis, for battle mode's second seat. */
    public Facing opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case RIGHT -> LEFT;
            case LEFT -> RIGHT;
        };
    }
}
