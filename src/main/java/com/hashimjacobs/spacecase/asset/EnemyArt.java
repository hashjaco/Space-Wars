package com.hashimjacobs.spacecase.asset;

/**
 * A regular hostile's animated hull in the CG set: a loop of numbered frames under
 * {@code sprites/enemy-cg/<galaxy>-<kind>[-side]/}, one design per galaxy and archetype rather
 * than one static frame per level.
 *
 * Shaped like {@link Explosion}: a directory, a frame count and a tick rate, no box. The box
 * stays on the level's {@link Sprite} the entity was built with, which is what the waves scale
 * and the side-on levels transpose. Constants are appended as the art lands; {@link #named}
 * returns null for a design that has not, and the renderer then draws the static sprite.
 */
public enum EnemyArt {
    ;

    private final String directory;
    private final int frameCount;
    private final int ticksPerFrame;

    EnemyArt(String directory, int frameCount, int ticksPerFrame) {
        this.directory = directory;
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
    }

    public String framePath(int oneBasedIndex) {
        return "/sprites/enemy-cg/" + directory + "/" + oneBasedIndex + ".png";
    }

    public int frameCount() {
        return frameCount;
    }

    public int frameIndexAt(int tick) {
        return (tick / ticksPerFrame) % frameCount;
    }

    /** The constant of that name, or null while its art has not landed. */
    public static EnemyArt named(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException notYet) {
            return null;
        }
    }
}
