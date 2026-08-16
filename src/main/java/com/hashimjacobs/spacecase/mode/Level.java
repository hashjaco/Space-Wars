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
 * The ten read as a journey -- approach a world, cross its air, its jungle, its canyon, its
 * undercity, then out through the rift, past its star, clear, along a dead world's terminator, and
 * finally down into the thing that lives under it. Killing the boss is what advances the level; see
 * {@code engine.SpawnDirector}. There is no eleventh: {@link #next()} wraps back to the first and
 * the director raises spawn pressure each time round, so the run stays endless and death remains
 * the only way it ends.
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
            Boss.FOUNDRY_WARDEN, 4),

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
     * The one leg flown side-on, along a dead world's terminator.
     *
     * The orientation and the art are a matched pair: this level's backdrop tiles horizontally
     * and its hulls are cut pointing left, so turning it top-down would seam the sky and leave
     * every enemy flying sideways.
     */
    DUST_REACH("Dust Reach",
            Sprite.L9_FAR, Sprite.L9_MID, Sprite.L9_NEAR,
            Sprite.L9_SCOUT, Sprite.L9_FIGHTER, Sprite.L9_CRUISER,
            Boss.DUNE_LEVIATHAN, 5, Orientation.RIGHT_TO_LEFT),

    /** Where the thing with three heads lives. The last place before the run loops. */
    HOLLOW_WOMB("Hollow Womb",
            Sprite.L10_FAR, Sprite.L10_MID, Sprite.L10_NEAR,
            Sprite.L10_SCOUT, Sprite.L10_FIGHTER, Sprite.L10_CRUISER,
            Boss.HYDRA, 5);

    private final String label;
    private final List<Sprite> layers;
    private final Sprite scout;
    private final Sprite fighter;
    private final Sprite cruiser;
    private final Boss boss;
    private final int wavesBeforeBoss;
    private final Orientation orientation;

    /** A level that runs top-down, which is all of them but the side-view leg. */
    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss) {
        this(label, far, mid, near, scout, fighter, cruiser, boss, wavesBeforeBoss,
                Orientation.TOP_DOWN);
    }

    Level(String label, Sprite far, Sprite mid, Sprite near,
          Sprite scout, Sprite fighter, Sprite cruiser, Boss boss, int wavesBeforeBoss,
          Orientation orientation) {
        this.label = label;
        this.layers = List.of(far, mid, near);
        this.scout = scout;
        this.fighter = fighter;
        this.cruiser = cruiser;
        this.boss = boss;
        this.wavesBeforeBoss = wavesBeforeBoss;
        this.orientation = orientation;
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

    /** Position in the run, counting from one, for display. */
    public int number() {
        int position = ordinal() + 1;
        return position;
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
