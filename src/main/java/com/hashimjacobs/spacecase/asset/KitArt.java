package com.hashimjacobs.spacecase.asset;

/**
 * A garage kit as an animated part in the CG set, drawn over the hull at its pose:
 * {@code sprites/kit-cg/<kit>/<pose>[-side]/<n>.png}, poses in {@link ChassisArt#POSES} order.
 * Constants are appended as the art lands; {@link #named} returns null until then and the kit
 * draws its generated decal.
 */
public enum KitArt {
    ;

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;

    KitArt(String directory, int frameCount, int ticksPerFrame) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
    }

    public String framePath(int poseIndex, boolean sideOn, int oneBasedIndex) {
        return "/sprites/kit-cg/" + directory + "/" + ChassisArt.POSES[poseIndex]
                + (sideOn ? "-side" : "") + "/" + oneBasedIndex + ".png";
    }

    public int frameCount() {
        return frameCount;
    }

    public int frameIndexAt(int tick) {
        return (tick / ticksPerFrame) % frameCount;
    }

    public static KitArt named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
    }
}
