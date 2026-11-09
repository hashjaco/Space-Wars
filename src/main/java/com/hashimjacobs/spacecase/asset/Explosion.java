package com.hashimjacobs.spacecase.asset;

/**
 * Frame-sequence explosions, stored as numbered PNGs in a directory per size.
 *
 * Frames must be addressed by index rather than by listing the directory: the old code used
 * Files.walk, which yields lexicographic order (1, 10, 11, ... 2, 20) so the animation played
 * scrambled, and which cannot enumerate a directory inside a packaged jar at all.
 */
public enum Explosion {

    SMALL("explosion-small", 25, 2),
    LARGE("explosion-large", 49, 1),
    /**
     * The blast every ship and rock now plays: one AI-drawn sequence (Sprixen), ten frames with
     * blended in-betweens, scaled by the thing that died. SMALL and LARGE above stay on disk as the
     * author's originals; {@code World} maps them here.
     */
    SMALL_SX("fx-explosion", 10, 3),
    LARGE_SX("fx-explosion", 10, 4),
    /** A bullet landing on a hull: a bright ray burst, a third of a second. Sprixen, see ASSETS.md. */
    HIT_SPARK("fx-hit-spark", 24, 1),
    /** The ring a dying flagship pushes out, under the fireballs. Sprixen. */
    BOSS_RING("fx-boss-shockwave", 47, 1),
    // The hydra's: a burst of gore where a shot lands on it, and acid hissing where its spit lands
    // on you. OpenArt stills animated on import (tools/openart/fx.py). Append only: World stores
    // pending explosions by ordinal.
    BLOOD_SPLAT("fx-blood-splat", 12, 2),
    ACID_SPLASH("fx-acid-splash", 12, 2),
    /** Not a one-shot: the player's engine flame, looped behind every CG hull (Renderer.drawFlame). */
    ENGINE_FLAME("fx-engine-flame", 8, 3);

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
