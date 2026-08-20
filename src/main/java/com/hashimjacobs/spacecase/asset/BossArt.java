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
    EXODUS_DREADNOUGHT("boss-exodus-dreadnought", 8, 6, 250, 188),

    /** Level 9, drawn side-on and opening leftward into the player. */
    DUNE_LEVIATHAN("boss-dune-leviathan", 8, 6, 214, 153),

    /** Level 10's torso. The necks and heads are not in these frames; they are drawn live. */
    HYDRA("boss-hydra", 8, 6, 236, 154),

    /**
     * One hydra head. Three are on screen at once, each its own target.
     *
     * Faster than the flagship frame rate: a jaw working at six ticks a frame reads as sluggish
     * where a hull throbbing at the same rate reads as idling.
     */
    HYDRA_HEAD("boss-hydra-head", 8, 4, 64, 64),

    // ---- Galaxy 2: Ashfall ---------------------------------------------------------------
    // Source frames for these are drawn at about 1.5x their on-screen size rather than the 2.1x
    // the originals use. The extra pixels only ever fed antialiasing Java2D was already doing,
    // and at eighty frames a galaxy they are real bytes in every clone of the repository.

    CINDER_WARDEN("boss-cinder-warden", 8, 6, 264, 135),
    ASH_REVENANT("boss-ash-revenant", 8, 6, 227, 167),
    SLAG_BARON("boss-slag-baron", 8, 6, 191, 200),
    VENT_CRAWLER("boss-vent-crawler", 8, 6, 253, 144),
    FORGE_OVERSEER("boss-forge-overseer", 8, 6, 235, 163),
    PYRE_SOVEREIGN("boss-pyre-sovereign", 8, 6, 253, 151),

    /**
     * Level 17, drawn already turned to face down a right-to-left arena.
     *
     * Width and height are transposed against the source canvas for that reason: the frames were
     * drawn nose-down at 268x316 and quarter-turned on the way out, so what the game decodes is
     * 316 wide. Getting this the wrong way round gives a ship whose hitbox is at right angles to
     * the picture of it, which no test would catch.
     */
    SUNWARD_LANCE("boss-sunward-lance", 8, 6, 211, 179),

    CORONA_HERALD("boss-corona-herald", 8, 6, 272, 159),
    EMBER_TITAN("boss-ember-titan", 8, 6, 237, 200),

    /** Level 20's rig. The cockpit is a separate target and is not in these frames. */
    FORGE_RIG("boss-forge-rig", 8, 6, 266, 200),

    /**
     * The man in the rig, shot separately once his arms are gone.
     *
     * Faster than the rig itself, for the reason a hydra head is faster than its torso: a small
     * thing animating at the same rate as a large one reads as sluggish.
     */
    FORGE_RIG_COCKPIT("boss-forge-rig-cockpit", 8, 4, 72, 72),

    /** One of the rig's two arm pods. Its own target, so its own frames. */
    FORGE_RIG_ARM("boss-forge-rig-arm", 8, 5, 78, 78),

    // ---- Galaxy 3: Cryonis ---------------------------------------------------------------
    // Ashfall's 1.5x source canvases, same reasoning.

    /**
     * Level 21, drawn already turned for a right-to-left arena, as the Sunward Lance is.
     *
     * The frames were drawn nose-down at 260x310 and quarter-turned on the way out, so what the
     * game decodes is 310 wide -- which is why the numbers here look transposed against the
     * generator's profile. See the note on SUNWARD_LANCE.
     */
    SHARD_CUTTER("boss-shard-cutter", 8, 6, 207, 173),

    FROST_HARRIER("boss-frost-harrier", 8, 6, 179, 207),
    GLACIER_BREAKER("boss-glacier-breaker", 8, 6, 256, 143),
    ICE_WRAITH("boss-ice-wraith", 8, 6, 232, 179),
    CRYO_MARSHAL("boss-cryo-marshal", 8, 6, 229, 165),
    TRENCH_HORROR("boss-trench-horror", 8, 6, 261, 149),
    GEYSER_MAW("boss-geyser-maw", 8, 6, 224, 195),
    HAIL_BASTION("boss-hail-bastion", 8, 6, 269, 153),

    /** Level 29, turned for the galaxy's second right-to-left arena. Same caveat as SHARD_CUTTER. */
    SHATTER_PROW("boss-shatter-prow", 8, 6, 220, 184),

    /** Level 30's torso. Like the hydra's, its necks and heads are drawn live, not in frames. */
    FROZEN_EMPRESS("boss-frozen-empress", 8, 6, 313, 207),

    /**
     * One of the Empress's four heads.
     *
     * Faster than the torso for the reason a hydra head is, and larger than one because there are
     * four of them spread across a wider arc -- at the hydra head's 64 they read as pinheads on
     * the end of long necks.
     */
    FROZEN_EMPRESS_HEAD("boss-frozen-empress-head", 8, 4, 104, 104),

    // ---- Galaxy 4: Tempest ---------------------------------------------------------------
    // The same 1.5x source canvases Ashfall and Cryonis use.

    SQUALL_WARDEN("boss-squall-warden", 8, 6, 248, 148),
    THUNDER_BROOD("boss-thunder-brood", 8, 6, 239, 175),
    EYEWALL_LANCE("boss-eyewall-lance", 8, 6, 169, 227),
    RING_REAVER("boss-ring-reaver", 8, 6, 269, 131),
    STATIC_CRAWLER("boss-static-crawler", 8, 6, 257, 147),
    MAGNETAR_MAW("boss-magnetar-maw", 8, 6, 220, 191),
    DOWNDRAFT_PROW("boss-downdraft-prow", 8, 6, 200, 191),

    /**
     * Level 38's rig: Vaunt again, in a bigger one. The cockpit and the arms are separate targets
     * and are not in these frames.
     *
     * 320 wide rather than anything larger because {@code entity.PilotedMech} strides to 0.8 of the
     * arena breadth and then adds half its own width, so a body past 398 walks its shoulder off the
     * edge of a top-down arena. Nothing in the suite would catch that.
     */
    STORM_RIG("boss-storm-rig", 8, 6, 320, 240),

    /** The man in the bigger rig. Same glass, same pilot; see FORGE_RIG_COCKPIT. */
    STORM_RIG_COCKPIT("boss-storm-rig-cockpit", 8, 4, 84, 84),

    /** One of the Storm-Rig's two arm pods. Its own art, reached through {@code Boss.armArt}. */
    STORM_RIG_ARM("boss-storm-rig-arm", 8, 5, 102, 102),

    /**
     * Level 39, turned for the galaxy's one right-to-left arena.
     *
     * Drawn nose-down at 268x322 and quarter-turned on the way out, so what the game decodes is 322
     * wide -- which is why these numbers look transposed against the generator's profile. Same
     * caveat as SUNWARD_LANCE and SHARD_CUTTER.
     */
    ARC_LANCE("boss-arc-lance", 8, 6, 215, 179),

    /**
     * Level 40's finale, striking down out of the cloud deck.
     *
     * 240 along the strike axis is not a free number: {@code entity.BurrowingWorm} reaches
     * {@code arenaDepth * strikeReach()} and then adds this, against a player spawning 130 short of
     * the back wall. At the Dune Leviathan's shared 0.62 that would put the maw past the player's
     * line; the serpent carries its own reach instead. See {@code BurrowingWorm.strikeReach}.
     */
    STORM_SERPENT("boss-storm-serpent", 8, 6, 320, 240);

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
