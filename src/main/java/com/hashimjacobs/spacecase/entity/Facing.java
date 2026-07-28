package com.hashimjacobs.spacecase.entity;

/**
 * Which way a ship points, and therefore which way its bullets travel.
 *
 * Needed for battle mode, where player two sits at the top of the arena and fires downward.
 */
public enum Facing {

    UP(-1),
    DOWN(1);

    private final int yDirection;

    Facing(int yDirection) {
        this.yDirection = yDirection;
    }

    /** -1 travels toward the top of the screen, +1 toward the bottom. */
    public int yDirection() {
        return yDirection;
    }

    /** Degrees to rotate the sprite so it visually points the right way. */
    public double rotationDegrees() {
        double degrees = this == UP ? 0 : 180;
        return degrees;
    }
}
