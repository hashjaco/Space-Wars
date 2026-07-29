package com.hashimjacobs.spacecase.asset;

/**
 * A boss's idle animation, stored as numbered PNGs in a directory per boss.
 *
 * Same shape as {@link Explosion}, and for the same reason: frames are addressed by index rather
 * than by listing the directory, because a directory listing yields lexicographic order (1, 10, 11,
 * ... 2, 20) and cannot enumerate a directory inside a packaged jar at all.
 *
 * The width and height are the on-screen size, which is also the boss's hitbox -- the source frames
 * are drawn larger and decoded down to this.
 */
public enum BossArt {

    SENTINEL("boss-sentinel", 8, 6, 210, 162),
    HIVE_MATRIARCH("boss-hive-matriarch", 8, 6, 226, 159),
    BLOOM_COLOSSUS("boss-bloom-colossus", 8, 6, 220, 187),
    SCRAP_HIVE("boss-scrap-hive", 8, 6, 226, 168),
    FOUNDRY_WARDEN("boss-foundry-warden", 8, 6, 230, 163),
    VOID_WEAVER("boss-void-weaver", 8, 6, 206, 172),
    CORE_TYRANT("boss-core-tyrant", 8, 6, 250, 185),
    EXODUS_DREADNOUGHT("boss-exodus-dreadnought", 8, 6, 250, 188);

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;
    private final double width;
    private final double height;

    BossArt(String directory, int frameCount, int ticksPerFrame, double width, double height) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
        this.width = width;
        this.height = height;
    }

    public String framePath(int oneBasedIndex) {
        String path = "/sprites/" + directory + "/" + oneBasedIndex + ".png";
        return path;
    }

    /** Frame to show at the given world tick. The sequence loops seamlessly. */
    public int frameIndexAt(int tick) {
        int index = (tick / ticksPerFrame) % frameCount;
        return index;
    }

    public int frameCount() {
        return frameCount;
    }

    public int ticksPerFrame() {
        return ticksPerFrame;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }
}
