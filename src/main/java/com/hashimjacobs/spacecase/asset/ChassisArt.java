package com.hashimjacobs.spacecase.asset;

/**
 * A player hull's animated CG loop, one constant per chassis: {@code sprites/player-cg/<chassis>/<n>.png},
 * straight, nose up, in militia green on the stock hull's 180x192 canvas. Paint, bank, the hit
 * flash and the side-on turn are derived on decode ({@link HullPaint}), so one loop serves all six
 * liveries, five poses and four cuts. Written by {@code tools/sprixen/hulls.py import}.
 *
 * The five poses are the banks in {@code PlayerShip.Lean} order, the same order the generator's
 * {@code POSES} and {@code Chassis.Names.POSES} use; {@code ChassisArtPosesTest} pins that.
 * Constants are appended as the art lands; {@link #named} returns null until then and the ship
 * flies its generated frames.
 */
public enum ChassisArt {
    // 16 frames of each premium loop, the stretches where Sprixen held the hull steady (hulls.py import).
    STOCK("stock", 16, 5),
    INTERCEPTOR("interceptor", 16, 5),
    GUNSHIP("gunship", 16, 5),
    TWIN_BOOM("twin-boom", 16, 5);

    public static final String[] POSES = {"bank-left", "left", "straight", "right", "bank-right"};

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;

    ChassisArt(String directory, int frameCount, int ticksPerFrame) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
    }

    public String framePath(int oneBasedIndex) {
        return "/sprites/player-cg/" + directory + "/" + oneBasedIndex + ".png";
    }

    public int frameCount() {
        return frameCount;
    }

    public int frameIndexAt(int tick) {
        return (tick / ticksPerFrame) % frameCount;
    }

    /** The constant of that name ({@code Chassis.name()}), or null while its art has not landed. */
    public static ChassisArt named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
    }
}
