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
            Boss.VAUNT, 6, WorldTemplate.CAVE);

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
