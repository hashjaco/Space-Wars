package com.hashimjacobs.spacecase.asset;

/**
 * Frame-sequence explosions, stored as numbered PNGs in a directory per size.
 *
 * Frames must be addressed by index rather than by listing the directory: the old code used
 * Files.walk, which yields lexicographic order (1, 10, 11, ... 2, 20) so the animation played
 * scrambled, and which cannot enumerate a directory inside a packaged jar at all.
 */
public enum Explosion {

    SMALL("explosion", 25, 2),
    LARGE("explosion2", 49, 1);

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;

    Explosion(String directory, int frameCount, int ticksPerFrame) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
    }

    public String framePath(int oneBasedIndex) {
        String path = "/sprites/" + directory + "/" + oneBasedIndex + ".png";
        return path;
    }

    public int frameCount() {
        return frameCount;
    }

    public int ticksPerFrame() {
        return ticksPerFrame;
    }
}
