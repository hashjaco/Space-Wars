package com.hashimjacobs.spacecase.mode;

import java.util.List;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;

/**
 * A place in a run: a sky, the inhabitants that defend it, a flagship, and how long you fight before
 * that flagship arrives.
 *
 * Levels come in blocks of ten, one block per {@link Galaxy}, and a run is one block: approach a
 * world, cross its air, its jungle, its canyon, its undercity, then out through the rift, past its
 * star, clear, along a dead world's terminator, and finally down into the thing that lives under
 * it. Killing the boss is what advances the level; see {@code engine.SpawnDirector}.
 *
 * {@link #next()} still wraps past the last level rather than stopping, and the director still
 * raises spawn pressure each time round. That is what a galaxy's border is measured against -- the
 * loop counter keeps working, and the run ends at the border because {@code engine.GameLoop}
 * notices the galaxy changed, not because the level sequence ran out.
 *
 * Nine is the odd one out, and deliberately: it is the only level flown side-on. That is carried by
 * {@link #orientation()} rather than by a special case anywhere in the engine.
 *
 * Pure data -- no asset loading -- so the enum is usable in tests that never start the JavaFX toolkit.
 */
public enum Level {

    ORBITAL_APPROACH("Orbital Approach",
            Sprite.L1_FAR, Sprite.L1_MID, Sprite.L1_NEAR,
            Sprite.L1_SCOUT, Sprite.L1_FIGHTER, Sprite.L1_CRUISER,
            Boss.SENTINEL, 3),

    VERDANT_AIRSPACE("Verdant Airspace",
            Sprite.L2_FAR, Sprite.L2_MID, Sprite.L2_NEAR,
            Sprite.L2_SCOUT, Sprite.L2_FIGHTER, Sprite.L2_CRUISER,
            Boss.HIVE_MATRIARCH, 3),

    CANOPY_DESCENT("Canopy Descent",
            Sprite.L3_FAR, Sprite.L3_MID, Sprite.L3_NEAR,
            Sprite.L3_SCOUT, Sprite.L3_FIGHTER, Sprite.L3_CRUISER,
            Boss.BLOOM_COLOSSUS, 3),

    RUST_CANYON("Rust Canyon",
            Sprite.L4_FAR, Sprite.L4_MID, Sprite.L4_NEAR,
            Sprite.L4_SCOUT, Sprite.L4_FIGHTER, Sprite.L4_CRUISER,
            Boss.SCRAP_HIVE, 4),

    UNDERCITY("Undercity",
            Sprite.L5_FAR, Sprite.L5_MID, Sprite.L5_NEAR,
            Sprite.L5_SCOUT, Sprite.L5_FIGHTER, Sprite.L5_CRUISER,
            Boss.FOUNDRY_WARDEN, 4, WorldTemplate.CAVE),

    VOID_RIFT("Void Rift",
            Sprite.L6_FAR, Sprite.L6_MID, Sprite.L6_NEAR,
            Sprite.L6_SCOUT, Sprite.L6_FIGHTER, Sprite.L6_CRUISER,
            Boss.VOID_WEAVER, 4),

    STAR_CORE("Star Core",
            Sprite.L7_FAR, Sprite.L7_MID, Sprite.L7_NEAR,
            Sprite.L7_SCOUT, Sprite.L7_FIGHTER, Sprite.L7_CRUISER,
            Boss.CORE_TYRANT, 5),

    ESCAPE_VECTOR("Escape Vector",
            Sprite.L8_FAR, Sprite.L8_MID, Sprite.L8_NEAR,
            Sprite.L8_SCOUT, Sprite.L8_FIGHTER, Sprite.L8_CRUISER,
            Boss.EXODUS_DREADNOUGHT, 5),

    /**
     * The one leg flown side-on, threading a canyon along a dead world's terminator.
     *
     * The orientation and the art are a matched pair: this level's backdrop tiles horizontally
     * and its hulls are cut pointing left, so turning it top-down would seam the sky and leave
     * every enemy flying sideways.
     *
     * Being side-on is also what makes it the one cave with a ceiling and a floor. In a top-down
     * level the lane runs across the screen, so rock closes in from the left and the right and it
     * reads as a shaft; here the lane is vertical, so the same terrain becomes overhangs above and
     * outcrops below. No new art either way -- the rock is drawn from the heightfield at runtime.
     */
    DUST_REACH("Dust Reach",
            Sprite.L9_FAR, Sprite.L9_MID, Sprite.L9_NEAR,
            Sprite.L9_SCOUT, Sprite.L9_FIGHTER, Sprite.L9_CRUISER,
            Boss.DUNE_LEVIATHAN, 5, Orientation.RIGHT_TO_LEFT, WorldTemplate.CAVE),

    /** Where the thing with three heads lives. The last place before the run loops. */
    HOLLOW_WOMB("Hollow Womb",
            Sprite.L10_FAR, Sprite.L10_MID, Sprite.L10_NEAR,
            Sprite.L10_SCOUT, Sprite.L10_FIGHTER, Sprite.L10_CRUISER,
            Boss.HYDRA, 5, WorldTemplate.CAVE),

    // ---- Galaxy 2: Ashfall (levels 11-20) --------------------------------------------------
    //
    // The galaxy with a ceiling. It opens on a belt, spends its middle underground or hugging the
    // ground, and closes in a caldera -- only Sunward Dive leaves the planet at all. Waves run
    // 4,4,4,4,5,5,5,5,5,6, so it is a longer galaxy than Verdance as well as a harder one.

    /** Cinder Belt: the first rocks, and the first new sky the game ever drew. */
    CINDER_BELT("Cinder Belt",
            Sprite.L11_FAR, Sprite.L11_MID, Sprite.L11_NEAR,
            Sprite.L11_SCOUT, Sprite.L11_FIGHTER, Sprite.L11_CRUISER,
            Boss.CINDER_WARDEN, 4),

    ASHFALL_SKY("Ashfall Sky",
            Sprite.L12_FAR, Sprite.L12_MID, Sprite.L12_NEAR,
            Sprite.L12_SCOUT, Sprite.L12_FIGHTER, Sprite.L12_CRUISER,
            Boss.ASH_REVENANT, 4),

    SLAGFIELDS("Slagfields",
            Sprite.L13_FAR, Sprite.L13_MID, Sprite.L13_NEAR,
            Sprite.L13_SCOUT, Sprite.L13_FIGHTER, Sprite.L13_CRUISER,
            Boss.SLAG_BARON, 4),

    /** Magma Vents: the galaxy's first tunnel, and the first rock in it that hurts. */
    MAGMA_VENTS("Magma Vents",
            Sprite.L14_FAR, Sprite.L14_MID, Sprite.L14_NEAR,
            Sprite.L14_SCOUT, Sprite.L14_FIGHTER, Sprite.L14_CRUISER,
            Boss.VENT_CRAWLER, 4, WorldTemplate.CAVE),

    THE_FORGEWORKS("The Forgeworks",
            Sprite.L15_FAR, Sprite.L15_MID, Sprite.L15_NEAR,
            Sprite.L15_SCOUT, Sprite.L15_FIGHTER, Sprite.L15_CRUISER,
            Boss.FORGE_OVERSEER, 5, WorldTemplate.CAVE),

    PYROCLAST("Pyroclast",
            Sprite.L16_FAR, Sprite.L16_MID, Sprite.L16_NEAR,
            Sprite.L16_SCOUT, Sprite.L16_FIGHTER, Sprite.L16_CRUISER,
            Boss.PYRE_SOVEREIGN, 5),

    /**
     * Sunward Dive: the galaxy's side-on leg, flown out past the star.
     *
     * The second level in the game to run this way, and the first built for it rather than
     * grandfathered. Everything it needs is a matched set: the sky tiles horizontally, the hostile
     * hulls are cut pointing left, their Sprite constants declare transposed sizes, and the
     * flagship's frames are turned once by the generator instead of rotated every frame. Change any
     * one of the four and the level looks broken in a way no test would catch.
     */
    SUNWARD_DIVE("Sunward Dive",
            Sprite.L17_FAR, Sprite.L17_MID, Sprite.L17_NEAR,
            Sprite.L17_SCOUT, Sprite.L17_FIGHTER, Sprite.L17_CRUISER,
            Boss.SUNWARD_LANCE, 5, Orientation.RIGHT_TO_LEFT),

    CORONAL_ARC("Coronal Arc",
            Sprite.L18_FAR, Sprite.L18_MID, Sprite.L18_NEAR,
            Sprite.L18_SCOUT, Sprite.L18_FIGHTER, Sprite.L18_CRUISER,
            Boss.CORONA_HERALD, 5),

    EMBER_CANYON("Ember Canyon",
            Sprite.L19_FAR, Sprite.L19_MID, Sprite.L19_NEAR,
            Sprite.L19_SCOUT, Sprite.L19_FIGHTER, Sprite.L19_CRUISER,
            Boss.EMBER_TITAN, 5),

    /** Caldera Heart: the bottom of the galaxy, and the man waiting at it. */
    CALDERA_HEART("Caldera Heart",
            Sprite.L20_FAR, Sprite.L20_MID, Sprite.L20_NEAR,
            Sprite.L20_SCOUT, Sprite.L20_FIGHTER, Sprite.L20_CRUISER,
            Boss.VAUNT, 6, WorldTemplate.CAVE),

    // ---- Galaxy 3: Cryonis (levels 21-30) ------------------------------------------------
    // Ice and water. The galaxy's structural idea is pacing rather than palette: the waves
    // alternate short and long, 3-5-3-5-3-5-4-6-4-6, so it feels different on the clock. It is
    // also the only galaxy flown side-on twice, at 21 and 29.

    /** Frost Ring: the first of the galaxy's two side-on legs. */
    FROST_RING("Frost Ring",
            Sprite.L21_FAR, Sprite.L21_MID, Sprite.L21_NEAR,
            Sprite.L21_SCOUT, Sprite.L21_FIGHTER, Sprite.L21_CRUISER,
            Boss.SHARD_CUTTER, 3, Orientation.RIGHT_TO_LEFT),

    RIME_SKY("Rime Sky",
            Sprite.L22_FAR, Sprite.L22_MID, Sprite.L22_NEAR,
            Sprite.L22_SCOUT, Sprite.L22_FIGHTER, Sprite.L22_CRUISER,
            Boss.FROST_HARRIER, 5),

    GLACIER_SHELF("Glacier Shelf",
            Sprite.L23_FAR, Sprite.L23_MID, Sprite.L23_NEAR,
            Sprite.L23_SCOUT, Sprite.L23_FIGHTER, Sprite.L23_CRUISER,
            Boss.GLACIER_BREAKER, 3),

    /** Under-Ice: the water leg, flown under the shelf rather than over it. */
    UNDER_ICE("Under-Ice",
            Sprite.L24_FAR, Sprite.L24_MID, Sprite.L24_NEAR,
            Sprite.L24_SCOUT, Sprite.L24_FIGHTER, Sprite.L24_CRUISER,
            Boss.ICE_WRAITH, 5),

    CREVASSE("Crevasse",
            Sprite.L25_FAR, Sprite.L25_MID, Sprite.L25_NEAR,
            Sprite.L25_SCOUT, Sprite.L25_FIGHTER, Sprite.L25_CRUISER,
            Boss.CRYO_MARSHAL, 3, WorldTemplate.CAVE),

    BLACK_TRENCH("Black Trench",
            Sprite.L26_FAR, Sprite.L26_MID, Sprite.L26_NEAR,
            Sprite.L26_SCOUT, Sprite.L26_FIGHTER, Sprite.L26_CRUISER,
            Boss.TRENCH_HORROR, 5, WorldTemplate.CAVE),

    GEYSER_FLATS("Geyser Flats",
            Sprite.L27_FAR, Sprite.L27_MID, Sprite.L27_NEAR,
            Sprite.L27_SCOUT, Sprite.L27_FIGHTER, Sprite.L27_CRUISER,
            Boss.GEYSER_MAW, 4),

    HAILWALL("Hailwall",
            Sprite.L28_FAR, Sprite.L28_MID, Sprite.L28_NEAR,
            Sprite.L28_SCOUT, Sprite.L28_FIGHTER, Sprite.L28_CRUISER,
            Boss.HAIL_BASTION, 6),

    /** Shatter Drift: the second side-on leg, which no other galaxy has. */
    SHATTER_DRIFT("Shatter Drift",
            Sprite.L29_FAR, Sprite.L29_MID, Sprite.L29_NEAR,
            Sprite.L29_SCOUT, Sprite.L29_FIGHTER, Sprite.L29_CRUISER,
            Boss.SHATTER_PROW, 4, Orientation.RIGHT_TO_LEFT),

    /** The Frozen Heart: the bottom of the galaxy, and the four-headed thing set into it. */
    THE_FROZEN_HEART("The Frozen Heart",
            Sprite.L30_FAR, Sprite.L30_MID, Sprite.L30_NEAR,
            Sprite.L30_SCOUT, Sprite.L30_FIGHTER, Sprite.L30_CRUISER,
            Boss.FROZEN_EMPRESS, 6, WorldTemplate.CAVE),

    // ---- Galaxy 4: Tempest (levels 31-40) ------------------------------------------------
    // Storm and gas giant, and the galaxy with no floor: six of the ten are inside cloud, exactly
    // one has ground under it, and the finale arrives out of the deck rather than flying in. Waves
    // run 4,4,5,5,5,5,6,6,6,6 -- a plain climb, because this galaxy's shape is its sky and not its
    // clock the way Cryonis's alternation was.

    /** Cloudwall: into the weather, and the first sky in the game with no horizon in it. */
    CLOUDWALL("Cloudwall",
            Sprite.L31_FAR, Sprite.L31_MID, Sprite.L31_NEAR,
            Sprite.L31_SCOUT, Sprite.L31_FIGHTER, Sprite.L31_CRUISER,
            Boss.SQUALL_WARDEN, 4),

    THUNDERHEAD("Thunderhead",
            Sprite.L32_FAR, Sprite.L32_MID, Sprite.L32_NEAR,
            Sprite.L32_SCOUT, Sprite.L32_FIGHTER, Sprite.L32_CRUISER,
            Boss.THUNDER_BROOD, 4),

    THE_EYE("The Eye",
            Sprite.L33_FAR, Sprite.L33_MID, Sprite.L33_NEAR,
            Sprite.L33_SCOUT, Sprite.L33_FIGHTER, Sprite.L33_CRUISER,
            Boss.EYEWALL_LANCE, 5),

    RING_DEBRIS("Ring Debris",
            Sprite.L34_FAR, Sprite.L34_MID, Sprite.L34_NEAR,
            Sprite.L34_SCOUT, Sprite.L34_FIGHTER, Sprite.L34_CRUISER,
            Boss.RING_REAVER, 5),

    /** Static Canyon: the one leg in the galaxy with ground under it. */
    STATIC_CANYON("Static Canyon",
            Sprite.L35_FAR, Sprite.L35_MID, Sprite.L35_NEAR,
            Sprite.L35_SCOUT, Sprite.L35_FIGHTER, Sprite.L35_CRUISER,
            Boss.STATIC_CRAWLER, 5),

    MAG_STORM_CAVERNS("Mag-Storm Caverns",
            Sprite.L36_FAR, Sprite.L36_MID, Sprite.L36_NEAR,
            Sprite.L36_SCOUT, Sprite.L36_FIGHTER, Sprite.L36_CRUISER,
            Boss.MAGNETAR_MAW, 5, WorldTemplate.CAVE),

    DEEP_DESCENT("Deep Descent",
            Sprite.L37_FAR, Sprite.L37_MID, Sprite.L37_NEAR,
            Sprite.L37_SCOUT, Sprite.L37_FIGHTER, Sprite.L37_CRUISER,
            Boss.DOWNDRAFT_PROW, 6),

    /** Upper Deck: back out on top of the weather, and the man who was waiting in Ashfall. */
    UPPER_DECK("Upper Deck",
            Sprite.L38_FAR, Sprite.L38_MID, Sprite.L38_NEAR,
            Sprite.L38_SCOUT, Sprite.L38_FIGHTER, Sprite.L38_CRUISER,
            Boss.VAUNT_IN_THE_STORM_RIG, 6),

    /**
     * Lightning Reach: the galaxy's side-on leg.
     *
     * Open space, for the reason levels 9 and 17 are: sky, ground and cavern each have a built-in
     * up and read as nonsense scrolled sideways, where stars look the same lying on their side. The
     * four things that have to agree are all set -- the sky tiles horizontally, the hulls are cut
     * pointing left, their Sprite constants declare transposed sizes, and the flagship's frames are
     * turned once by the generator rather than rotated per frame.
     */
    LIGHTNING_REACH("Lightning Reach",
            Sprite.L39_FAR, Sprite.L39_MID, Sprite.L39_NEAR,
            Sprite.L39_SCOUT, Sprite.L39_FIGHTER, Sprite.L39_CRUISER,
            Boss.ARC_LANCE, 6, Orientation.RIGHT_TO_LEFT),

    /** Storm Crown: the top of the weather, and the thing that comes down through it. */
    STORM_CROWN("Storm Crown",
            Sprite.L40_FAR, Sprite.L40_MID, Sprite.L40_NEAR,
            Sprite.L40_SCOUT, Sprite.L40_FIGHTER, Sprite.L40_CRUISER,
            Boss.STORM_SERPENT, 6),

    // ---- Galaxy 5: Null (levels 41-50) ---------------------------------------------------
    // No sky and no ground: not one ATMOSPHERE or SURFACE backdrop in the galaxy, so there is
    // nothing to fly over and nothing overhead, and the only enclosures are dead structures.
    //
    // The waves run 6,6,6,6,5,5,5,4,4,4 -- the only galaxy that shortens as it goes. Verdance and
    // Tempest climb and Cryonis alternates; this one falls in, so the bosses arrive faster and
    // faster. It is not a difficulty cut: the bosses are the hard part of a leg and this delivers
    // more of them per minute, against the steepest health ladder in the game.

    /** Dead Belt: a graveyard of a belt, and the last ordinary sky in the campaign. */
    DEAD_BELT("Dead Belt",
            Sprite.L41_FAR, Sprite.L41_MID, Sprite.L41_NEAR,
            Sprite.L41_SCOUT, Sprite.L41_FIGHTER, Sprite.L41_CRUISER,
            Boss.BONEPICKER, 6),

    /** Hulk Drift: a dead fleet, and something living in it. */
    HULK_DRIFT("Hulk Drift",
            Sprite.L42_FAR, Sprite.L42_MID, Sprite.L42_NEAR,
            Sprite.L42_SCOUT, Sprite.L42_FIGHTER, Sprite.L42_CRUISER,
            Boss.HULK_CHOIR, 6),

    /** Shroud: a world that went out. The galaxy's one planet, and it is not lit. */
    SHROUD("Shroud",
            Sprite.L43_FAR, Sprite.L43_MID, Sprite.L43_NEAR,
            Sprite.L43_SCOUT, Sprite.L43_FIGHTER, Sprite.L43_CRUISER,
            Boss.SHROUDMAW, 6),

    /** Lens Corridor: the first sight of the thing this galaxy is about. */
    LENS_CORRIDOR("Lens Corridor",
            Sprite.L44_FAR, Sprite.L44_MID, Sprite.L44_NEAR,
            Sprite.L44_SCOUT, Sprite.L44_FIGHTER, Sprite.L44_CRUISER,
            Boss.LENSBREAKER, 6),

    /**
     * Tidal Shear: the galaxy's side-on leg.
     *
     * A belt for the reason levels 9, 17 and 39 are open space: rock and stars look the same lying
     * on their side, where a sky, a ground or a cavern each has a built-in up. This galaxy has no
     * sky or ground to get wrong anyway, and its two enclosures are needed elsewhere.
     *
     * The four things that have to agree are all set -- the belt tiles horizontally, the hulls are
     * cut pointing left, their Sprite constants declare transposed sizes, and the flagship's frames
     * are turned once by the generator rather than rotated per frame. It fields a warship rather
     * than a creature because CreatureProfile has no sideways field, so it cannot guard one.
     */
    TIDAL_SHEAR("Tidal Shear",
            Sprite.L45_FAR, Sprite.L45_MID, Sprite.L45_NEAR,
            Sprite.L45_SCOUT, Sprite.L45_FIGHTER, Sprite.L45_CRUISER,
            Boss.TIDEWRACK, 5, Orientation.RIGHT_TO_LEFT),

    /** The Shell: inside a dead structure, which is the only kind of enclosure out here. */
    THE_SHELL("The Shell",
            Sprite.L46_FAR, Sprite.L46_MID, Sprite.L46_NEAR,
            Sprite.L46_SCOUT, Sprite.L46_FIGHTER, Sprite.L46_CRUISER,
            Boss.SHELLBORN, 5, WorldTemplate.CAVE),

    /** Ergosphere: close enough that spacetime is visibly turning. */
    ERGOSPHERE("Ergosphere",
            Sprite.L47_FAR, Sprite.L47_MID, Sprite.L47_NEAR,
            Sprite.L47_SCOUT, Sprite.L47_FIGHTER, Sprite.L47_CRUISER,
            Boss.FRAME_DRAG, 5),

    /** Photon Ring: the brightest sky in the game, and the ring is all of it. */
    PHOTON_RING("Photon Ring",
            Sprite.L48_FAR, Sprite.L48_MID, Sprite.L48_NEAR,
            Sprite.L48_SCOUT, Sprite.L48_FIGHTER, Sprite.L48_CRUISER,
            Boss.PHOTON_HALO, 4),

    /** The Throat: the second dead structure, and the last enclosure in the campaign. */
    THE_THROAT("The Throat",
            Sprite.L49_FAR, Sprite.L49_MID, Sprite.L49_NEAR,
            Sprite.L49_SCOUT, Sprite.L49_FIGHTER, Sprite.L49_CRUISER,
            Boss.GULLET, 4, WorldTemplate.CAVE),

    /** Event Horizon: the last level. The hole fills the frame and Aeon is in front of it. */
    EVENT_HORIZON("Event Horizon",
            Sprite.L50_FAR, Sprite.L50_MID, Sprite.L50_NEAR,
            Sprite.L50_SCOUT, Sprite.L50_FIGHTER, Sprite.L50_CRUISER,
            Boss.AEON, 4);

    private final String label;
    private final List<Sprite> layers;
    private final Sprite scout;
    private final Sprite fighter;
    private final Sprite cruiser;
    private final Boss boss;
    private final int wavesBeforeBoss;
    private final Orientation orientation;
    private final WorldTemplate template;

    /** A level that runs top-down through open space, which is most of them. */
    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss) {
        this(label, far, mid, near, scout, fighter, cruiser, boss, wavesBeforeBoss,
                Orientation.TOP_DOWN, WorldTemplate.OPEN_FIELD);
    }

    /** A level that runs the other way, through open space. */
    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss,
          Orientation orientation) {
        this(label, far, mid, near, scout, fighter, cruiser, boss, wavesBeforeBoss,
                orientation, WorldTemplate.OPEN_FIELD);
    }

    /** A top-down level with rock in it. */
    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss,
          WorldTemplate template) {
        this(label, far, mid, near, scout, fighter, cruiser, boss, wavesBeforeBoss,
                Orientation.TOP_DOWN, template);
    }

    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss,
          Orientation orientation, WorldTemplate template) {
        this.label = label;
        this.layers = List.of(far, mid, near);
        this.scout = scout;
        this.fighter = fighter;
        this.cruiser = cruiser;
        this.boss = boss;
        this.wavesBeforeBoss = wavesBeforeBoss;
        this.orientation = orientation;
        this.template = template;
    }

    /**
     * Which way this level runs.
     *
     * Coupled to the art: a level's backdrop layers tile on one axis only, so flipping this
     * without regenerating them puts a seam in the sky once per wrap.
     */
    public Orientation orientation() {
        return orientation;
    }

    public String label() {
        return label;
    }

    /**
     * Position in the whole campaign, counting from one.
     *
     * This is the number the art directory is named after -- level-23 is the twenty-third constant
     * -- so it keeps rising across galaxy borders. It is not what the player is shown as "level 3",
     * and it is not what the debrief is paid on; both of those want {@link #indexInGalaxy()}.
     */
    public int number() {
        int position = ordinal() + 1;
        return position;
    }

    /**
     * What shape this level is: open space, or rock closing in from both sides.
     *
     * The structural sibling of {@link #orientation()}, and carried the same way -- as data the
     * engine reads, rather than as a branch anywhere in it.
     */
    public WorldTemplate template() {
        return template;
    }

    /** Which galaxy this level belongs to. Blocks of ten, so the ordinal decides it. */
    public Galaxy galaxy() {
        return Galaxy.values()[ordinal() / Galaxy.LEVELS_PER_GALAXY];
    }

    /**
     * Position within its galaxy, one to ten.
     *
     * What the HUD shows and what {@code mode.Debrief} is paid on. The bounty and the credits are
     * tuned against a one-to-ten range, so feeding them {@link #number()} would have level 47 pay
     * out nearly five times what the garage was priced for.
     */
    public int indexInGalaxy() {
        return ordinal() % Galaxy.LEVELS_PER_GALAXY + 1;
    }

    /** Parallax layers, deepest first. The renderer scrolls each one at its own rate. */
    public List<Sprite> layers() {
        return layers;
    }

    /** This level's art for an enemy archetype. The archetype itself carries health, speed and score. */
    public Sprite enemySprite(EnemyShip.EnemyKind kind) {
        return switch (kind) {
            case SCOUT -> scout;
            case FIGHTER -> fighter;
            case CRUISER -> cruiser;
        };
    }

    public Boss boss() {
        return boss;
    }

    public int wavesBeforeBoss() {
        return wavesBeforeBoss;
    }

    /** The level after this one, wrapping past the last back to the first. */
    public Level next() {
        Level[] all = values();
        Level following = all[(ordinal() + 1) % all.length];
        return following;
    }
}
