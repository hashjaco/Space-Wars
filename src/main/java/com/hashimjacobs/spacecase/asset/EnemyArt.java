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
    // Verdance: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    VERDANCE_SCOUT("verdance-scout", 24, 4),
    VERDANCE_SCOUT_SIDE("verdance-scout-side", 24, 4),
    VERDANCE_FIGHTER("verdance-fighter", 24, 4),
    VERDANCE_FIGHTER_SIDE("verdance-fighter-side", 24, 4),
    VERDANCE_CRUISER("verdance-cruiser", 24, 4),
    VERDANCE_CRUISER_SIDE("verdance-cruiser-side", 24, 4),
    // Ashfall: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    ASHFALL_SCOUT("ashfall-scout", 24, 4),
    ASHFALL_SCOUT_SIDE("ashfall-scout-side", 24, 4),
    ASHFALL_FIGHTER("ashfall-fighter", 24, 4),
    ASHFALL_FIGHTER_SIDE("ashfall-fighter-side", 24, 4),
    ASHFALL_CRUISER("ashfall-cruiser", 24, 4),
    ASHFALL_CRUISER_SIDE("ashfall-cruiser-side", 24, 4),
    // Cryonis: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    CRYONIS_SCOUT("cryonis-scout", 24, 4),
    CRYONIS_SCOUT_SIDE("cryonis-scout-side", 24, 4),
    CRYONIS_FIGHTER("cryonis-fighter", 24, 4),
    CRYONIS_FIGHTER_SIDE("cryonis-fighter-side", 24, 4),
    CRYONIS_CRUISER("cryonis-cruiser", 24, 4),
    CRYONIS_CRUISER_SIDE("cryonis-cruiser-side", 24, 4),
    // Tempest: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    TEMPEST_SCOUT("tempest-scout", 24, 4),
    TEMPEST_SCOUT_SIDE("tempest-scout-side", 24, 4),
    TEMPEST_FIGHTER("tempest-fighter", 24, 4),
    TEMPEST_FIGHTER_SIDE("tempest-fighter-side", 24, 4),
    TEMPEST_CRUISER("tempest-cruiser", 24, 4),
    TEMPEST_CRUISER_SIDE("tempest-cruiser-side", 24, 4),
    // Null: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    NULL_SCOUT("null-scout", 24, 4),
    NULL_SCOUT_SIDE("null-scout-side", 24, 4),
    NULL_FIGHTER("null-fighter", 24, 4),
    NULL_FIGHTER_SIDE("null-fighter-side", 24, 4),
    NULL_CRUISER("null-cruiser", 24, 4),
    NULL_CRUISER_SIDE("null-cruiser-side", 24, 4);

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
