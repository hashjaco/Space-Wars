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
    VERDANCE_SCOUT_SIDE("verdance-scout-side", 24, 4, false),
    VERDANCE_FIGHTER("verdance-fighter", 24, 4),
    VERDANCE_FIGHTER_SIDE("verdance-fighter-side", 24, 4, true),
    VERDANCE_CRUISER("verdance-cruiser", 24, 4),
    VERDANCE_CRUISER_SIDE("verdance-cruiser-side", 24, 4, true),
    // Ashfall: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    ASHFALL_SCOUT("ashfall-scout", 24, 4),
    ASHFALL_SCOUT_SIDE("ashfall-scout-side", 24, 4, false),
    ASHFALL_FIGHTER("ashfall-fighter", 24, 4),
    ASHFALL_FIGHTER_SIDE("ashfall-fighter-side", 24, 4, true),
    ASHFALL_CRUISER("ashfall-cruiser", 24, 4),
    ASHFALL_CRUISER_SIDE("ashfall-cruiser-side", 24, 4, true),
    // Cryonis: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    CRYONIS_SCOUT("cryonis-scout", 24, 4),
    CRYONIS_SCOUT_SIDE("cryonis-scout-side", 24, 4, true),
    CRYONIS_FIGHTER("cryonis-fighter", 24, 4),
    CRYONIS_FIGHTER_SIDE("cryonis-fighter-side", 24, 4, false),
    CRYONIS_CRUISER("cryonis-cruiser", 24, 4),
    CRYONIS_CRUISER_SIDE("cryonis-cruiser-side", 24, 4, true),
    // Tempest: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    TEMPEST_SCOUT("tempest-scout", 24, 4),
    TEMPEST_SCOUT_SIDE("tempest-scout-side", 24, 4, false),
    TEMPEST_FIGHTER("tempest-fighter", 24, 4),
    TEMPEST_FIGHTER_SIDE("tempest-fighter-side", 24, 4, false),
    TEMPEST_CRUISER("tempest-cruiser", 24, 4),
    TEMPEST_CRUISER_SIDE("tempest-cruiser-side", 24, 4, true),
    // Null: OpenArt concept, Tripo model, Blender loop (tools/tripo/enemies.py).
    NULL_SCOUT("null-scout", 24, 4),
    NULL_SCOUT_SIDE("null-scout-side", 24, 4, true),
    NULL_FIGHTER("null-fighter", 24, 4),
    NULL_FIGHTER_SIDE("null-fighter-side", 24, 4, false),
    NULL_CRUISER("null-cruiser", 24, 4),
    NULL_CRUISER_SIDE("null-cruiser-side", 24, 4, true);

    /** A profile's two extra loops, stored after the idle loop: firing, then flinching. */
    public static final int ATTACK_FRAMES = 12;
    public static final int HURT_FRAMES = 12;
    /** How long a shot or a hit holds its loop, in ticks: two ticks a frame. */
    public static final int POSE_TICKS = 24;

    private final String directory;
    private final int idleFrames;
    private final int ticksPerFrame;
    private final boolean profile;
    private final boolean flame;

    /** A plan view for the top-down levels: one idle loop. */
    EnemyArt(String directory, int frameCount, int ticksPerFrame) {
        this(directory, frameCount, ticksPerFrame, false, false);
    }

    /**
     * A true profile for the side-on levels, nose left, with attack and hurt loops beside the idle
     * one (tools/tripo/enemies.py render-side).
     *
     * @param flame whether the renderer adds an engine plume at the stern: false where the model
     *              came out of Tripo with one already on it
     */
    EnemyArt(String directory, int frameCount, int ticksPerFrame, boolean flame) {
        this(directory, frameCount, ticksPerFrame, true, flame);
    }

    EnemyArt(String directory, int idleFrames, int ticksPerFrame, boolean profile, boolean flame) {
        this.directory = directory;
        this.idleFrames = idleFrames;
        this.ticksPerFrame = ticksPerFrame;
        this.profile = profile;
        this.flame = flame;
    }

    /** One numbering across every loop, so loading and the provenance test need not know about poses. */
    public String framePath(int oneBasedIndex) {
        String base = "/sprites/enemy-cg/" + directory + "/";
        if (oneBasedIndex <= idleFrames) {
            return base + oneBasedIndex + ".png";
        }
        int past = oneBasedIndex - idleFrames;
        return past <= ATTACK_FRAMES ? base + "attack/" + past + ".png"
                : base + "hurt/" + (past - ATTACK_FRAMES) + ".png";
    }

    public int frameCount() {
        return profile ? idleFrames + ATTACK_FRAMES + HURT_FRAMES : idleFrames;
    }

    public boolean profile() {
        return profile;
    }

    public boolean flame() {
        return flame;
    }

    /** The firing loop's frame with this many of {@link #POSE_TICKS} left to run. */
    public int attackFrameAt(int ticksLeft) {
        return idleFrames + Math.min(ATTACK_FRAMES - 1, (POSE_TICKS - ticksLeft) * ATTACK_FRAMES / POSE_TICKS);
    }

    /** The flinch's frame with this many of {@link #POSE_TICKS} left to run. */
    public int hurtFrameAt(int ticksLeft) {
        return idleFrames + ATTACK_FRAMES + Math.min(HURT_FRAMES - 1, (POSE_TICKS - ticksLeft) * HURT_FRAMES / POSE_TICKS);
    }

    public int frameIndexAt(int tick) {
        return (tick / ticksPerFrame) % idleFrames;
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
