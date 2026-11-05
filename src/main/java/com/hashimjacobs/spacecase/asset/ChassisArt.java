package com.hashimjacobs.spacecase.asset;

/**
 * A player hull's animated frames in the CG set, one constant per chassis and paint:
 * {@code sprites/player-cg/<chassis>-<livery>/<pose>[-hit][-side]/<n>.png}.
 *
 * The five poses are the banks in {@code PlayerShip.Lean} order, the same order the generator's
 * {@code POSES} and {@code Chassis.Names.POSES} use; {@code ChassisArtTest} pins that. Every pose
 * and variant of one constant carries the same frame count. Frames decode to the stock hull's box
 * ({@code Sprite.P1_STRAIGHT}), transposed for the side cut, so the ship's size on screen is the
 * entity's box as it is today. Constants are appended as the art lands; {@link #named} returns
 * null until then and the ship flies its generated frames.
 */
public enum ChassisArt {
    ;

    public static final String[] POSES = {"bank-left", "left", "straight", "right", "bank-right"};

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;

    ChassisArt(String directory, int frameCount, int ticksPerFrame) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
    }

    public String framePath(int poseIndex, boolean hit, boolean sideOn, int oneBasedIndex) {
        return "/sprites/player-cg/" + directory + "/" + POSES[poseIndex] + (hit ? "-hit" : "")
                + (sideOn ? "-side" : "") + "/" + oneBasedIndex + ".png";
    }

    public int frameCount() {
        return frameCount;
    }

    public int frameIndexAt(int tick) {
        return (tick / ticksPerFrame) % frameCount;
    }

    /** The constant of that name ({@code <CHASSIS>_<LIVERY>}), or null while its art has not landed. */
    public static ChassisArt named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
    }
}
