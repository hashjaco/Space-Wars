package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.asset.MusicCue;

/**
 * The flagship that ends a level, and how it fights.
 *
 * One per level, in level order, so health rises as you get further. Each carries three
 * {@link BossPhase}s applied in turn as its health falls, which is what keeps eight fights against
 * the same construction from feeling like one fight with a bigger number.
 *
 * Pure data on purpose: nothing here touches the asset loader, so the enum is usable in tests that
 * never start the JavaFX toolkit.
 */
public enum Boss {

    /** Level 1. The original fight, unchanged, so the opening minutes stay tuned. */
    SENTINEL("Sentinel", BossArt.SENTINEL, 600, 500,
            BossPhase.SPREAD, BossPhase.SWEEPING_FAN, BossPhase.AIMED_BURST),

    /** Level 2. Broods escorts while it is healthy, then fights for itself. */
    HIVE_MATRIARCH("Hive Matriarch", BossArt.HIVE_MATRIARCH, 720, 620,
            BossPhase.SPREAD, BossPhase.SPAWNER, BossPhase.AIMED_BURST),

    /** Level 3. Opens with a curtain and never gives a straight line to sit in. */
    BLOOM_COLOSSUS("Bloom Colossus", BossArt.BLOOM_COLOSSUS, 840, 740,
            BossPhase.RING, BossPhase.SPREAD, BossPhase.SPIRAL),

    /** Level 4. Hides behind escorts while it is healthy and fights for itself once they are gone. */
    SCRAP_HIVE("Scrap Hive", BossArt.SCRAP_HIVE, 960, 860,
            BossPhase.SPAWNER, BossPhase.SPREAD, BossPhase.AIMED_BURST),

    /** Level 5. Sweeps, then curtains, then picks you out: no safe column at any health. */
    FOUNDRY_WARDEN("Foundry Warden", BossArt.FOUNDRY_WARDEN, 1080, 980,
            BossPhase.SWEEPING_FAN, BossPhase.RING, BossPhase.AIMED_BURST),

    /** Level 6. Never fires straight: an arc, then a sweep, then a curtain. */
    VOID_WEAVER("Void Weaver", BossArt.VOID_WEAVER, 1200, 1100,
            BossPhase.SPIRAL, BossPhase.SWEEPING_FAN, BossPhase.RING),

    /** Level 7. Opens at its most dangerous and calls in help on the way down. */
    CORE_TYRANT("Core Tyrant", BossArt.CORE_TYRANT, 1350, 1250,
            BossPhase.RING, BossPhase.SPAWNER, BossPhase.AIMED_BURST),

    /** Level 8. The last thing between the run and its next loop, and armed like it. */
    EXODUS_DREADNOUGHT("Exodus Dreadnought", BossArt.EXODUS_DREADNOUGHT, 1500, 1500,
            BossPhase.SWEEPING_FAN, BossPhase.SPIRAL, BossPhase.RING),

    /**
     * Level 9. A burrowing thing that lunges out of the right-hand wall and withdraws into it.
     *
     * The first boss in the run that is alive rather than built, and the first fought side-on.
     */
    DUNE_LEVIATHAN("Dune Leviathan", BossArt.DUNE_LEVIATHAN, 1700, 1600,
            BossPhase.SPREAD, BossPhase.AIMED_BURST, BossPhase.SPIRAL),

    /**
     * Level 10. Three heads on necks of their own, each its own target.
     *
     * The body is armoured until every head is down; killing one silences that head's fire. It
     * opens on SPAWNER deliberately -- the torso vents crawlers while the heads do the shooting,
     * so there is never a fourth gun in the mix.
     */
    HYDRA("Hydra", BossArt.HYDRA, 2000, 1900,
            BossPhase.SPAWNER, BossPhase.RING, BossPhase.AIMED_BURST,
            3, BossArt.HYDRA_HEAD),

    // ---- Galaxy 2: Ashfall ---------------------------------------------------------------
    // Health and score keep rising with the enum order, which BossTest holds them to, so the
    // ladder continues from the hydra rather than restarting. Sixty points of health a step
    // inside the galaxy and a visible jump at its finale; the per-level difficulty ramp in
    // prefs.Difficulty restarts each galaxy so the two do not compound.
    //
    // Phase orders are picked so no two flagships in the galaxy open the same way.

    CINDER_WARDEN("Cinder Warden", BossArt.CINDER_WARDEN, 2060, 1950,
            BossPhase.SPREAD, BossPhase.SWEEPING_FAN, BossPhase.AIMED_BURST),

    ASH_REVENANT("Ash Revenant", BossArt.ASH_REVENANT, 2120, 2000,
            BossPhase.RING, BossPhase.SPREAD, BossPhase.SPIRAL),

    SLAG_BARON("Slag Baron", BossArt.SLAG_BARON, 2180, 2050,
            BossPhase.SWEEPING_FAN, BossPhase.SPAWNER, BossPhase.AIMED_BURST),

    VENT_CRAWLER("Vent Crawler", BossArt.VENT_CRAWLER, 2240, 2100,
            BossPhase.SPIRAL, BossPhase.RING, BossPhase.SPREAD),

    FORGE_OVERSEER("Forge Overseer", BossArt.FORGE_OVERSEER, 2300, 2150,
            BossPhase.SPAWNER, BossPhase.SWEEPING_FAN, BossPhase.RING),

    PYRE_SOVEREIGN("Pyre Sovereign", BossArt.PYRE_SOVEREIGN, 2360, 2200,
            BossPhase.RING, BossPhase.SPIRAL, BossPhase.AIMED_BURST),

    SUNWARD_LANCE("Sunward Lance", BossArt.SUNWARD_LANCE, 2420, 2250,
            BossPhase.AIMED_BURST, BossPhase.SWEEPING_FAN, BossPhase.SPIRAL),

    CORONA_HERALD("Corona Herald", BossArt.CORONA_HERALD, 2480, 2300,
            BossPhase.SWEEPING_FAN, BossPhase.RING, BossPhase.SPAWNER),

    EMBER_TITAN("Ember Titan", BossArt.EMBER_TITAN, 2540, 2350,
            BossPhase.SPREAD, BossPhase.SPAWNER, BossPhase.SPIRAL),

    /**
     * Vaunt, in the Forge-Rig. The galaxy's finale, and the first fight with somebody in it.
     *
     * Two arm pods guard the body, which is the machinery every multi-part boss already has -- a
     * flagship refuses damage while any part lives. What is new is the cockpit: a third part that
     * refuses damage of its own while either arm survives, so the fight reads as break the guard,
     * then shoot the man. See {@code entity.PilotedMech}.
     *
     * A jump rather than a step at the health ladder, because a galaxy should end on one.
     */
    VAUNT("Vaunt, in the Forge-Rig", BossArt.FORGE_RIG, 2800, 2550,
            BossPhase.SWEEPING_FAN, BossPhase.AIMED_BURST, BossPhase.RING,
            2, BossArt.FORGE_RIG_COCKPIT),

    // ---- Galaxy 3: Cryonis ---------------------------------------------------------------
    // The ladder continues rather than restarting: sixty a step inside the galaxy again, opening
    // above Vaunt, and a jump at the finale. Score rises fifty a step for the same reason.
    //
    // Phase orders are picked so no two flagships in the galaxy open the same way, as Ashfall's
    // are -- and so the two side-on legs, 21 and 29, do not share an opener either.

    SHARD_CUTTER("Shard Cutter", BossArt.SHARD_CUTTER, 2860, 2600,
            BossPhase.SPREAD, BossPhase.SPIRAL, BossPhase.AIMED_BURST),

    FROST_HARRIER("Frost Harrier", BossArt.FROST_HARRIER, 2920, 2650,
            BossPhase.SPIRAL, BossPhase.SWEEPING_FAN, BossPhase.RING),

    GLACIER_BREAKER("Glacier Breaker", BossArt.GLACIER_BREAKER, 2980, 2700,
            BossPhase.SWEEPING_FAN, BossPhase.SPREAD, BossPhase.SPAWNER),

    ICE_WRAITH("Ice Wraith", BossArt.ICE_WRAITH, 3040, 2750,
            BossPhase.RING, BossPhase.AIMED_BURST, BossPhase.SPIRAL),

    CRYO_MARSHAL("Cryo Marshal", BossArt.CRYO_MARSHAL, 3100, 2800,
            BossPhase.AIMED_BURST, BossPhase.SPAWNER, BossPhase.SWEEPING_FAN),

    TRENCH_HORROR("Trench Horror", BossArt.TRENCH_HORROR, 3160, 2850,
            BossPhase.SPAWNER, BossPhase.RING, BossPhase.SPREAD),

    GEYSER_MAW("Geyser Maw", BossArt.GEYSER_MAW, 3220, 2900,
            BossPhase.SPREAD, BossPhase.RING, BossPhase.SPAWNER),

    HAIL_BASTION("Hail Bastion", BossArt.HAIL_BASTION, 3280, 2950,
            BossPhase.SWEEPING_FAN, BossPhase.SPIRAL, BossPhase.AIMED_BURST),

    SHATTER_PROW("Shatter Prow", BossArt.SHATTER_PROW, 3340, 3000,
            BossPhase.SPIRAL, BossPhase.SPREAD, BossPhase.RING),

    /**
     * The Frozen Empress. The galaxy's finale, and the second multi-headed fight in the game.
     *
     * Four heads rather than the hydra's three, which is a data row and not a new class: BossHead
     * spreads any number of necks across its arc, and EnemyShip builds one part per head. The two
     * things that had to be got right are outside this file -- her torso is drawn with four
     * sockets at the positions BossHead roots necks at, and EnemyWeapons derives the part cooldown
     * from the head count so a fourth head does not simply add a fourth gun's worth of fire.
     *
     * A jump rather than a step, because a galaxy should end on one.
     */
    FROZEN_EMPRESS("The Frozen Empress", BossArt.FROZEN_EMPRESS, 3700, 3200,
            BossPhase.SPAWNER, BossPhase.SPREAD, BossPhase.RING,
            4, BossArt.FROZEN_EMPRESS_HEAD);

    private final String label;
    private final BossArt art;
    private final int health;
    private final int scoreValue;
    private final BossPhase openingPhase;
    private final BossPhase middlePhase;
    private final BossPhase finalPhase;
    private final int heads;
    private final BossArt headArt;

    /** A flagship that is one piece, which is all of them but the hydra. */
    Boss(String label, BossArt art, int health, int scoreValue,
         BossPhase openingPhase, BossPhase middlePhase, BossPhase finalPhase) {
        this(label, art, health, scoreValue, openingPhase, middlePhase, finalPhase, 0, null);
    }

    Boss(String label, BossArt art, int health, int scoreValue,
         BossPhase openingPhase, BossPhase middlePhase, BossPhase finalPhase,
         int heads, BossArt headArt) {
        this.label = label;
        this.art = art;
        this.health = health;
        this.scoreValue = scoreValue;
        this.openingPhase = openingPhase;
        this.middlePhase = middlePhase;
        this.finalPhase = finalPhase;
        this.heads = heads;
        this.headArt = headArt;
    }

    /** Separately targetable heads on their own necks; zero for a flagship that is one piece. */
    public int heads() {
        return heads;
    }

    /** The frames one head animates through, or null when this flagship has none. */
    public BossArt headArt() {
        return headArt;
    }

    /**
     * Which cue plays while this flagship is on screen.
     *
     * A method rather than a ninth constructor argument, so the eight original constants stay as
     * they were. Putting the choice on {@code MusicCue} instead would point {@code asset} at
     * {@code entity} and close a package cycle.
     */
    public MusicCue music() {
        return switch (this) {
            case HYDRA -> MusicCue.HYDRA_BOSS;
            case DUNE_LEVIATHAN -> MusicCue.LEVIATHAN_BOSS;
            default -> MusicCue.BOSS;
        };
    }

    /** Phase for this boss at the given 0..1 remaining-health fraction. */
    public BossPhase phaseFor(double healthFraction) {
        if (healthFraction > 0.66) {
            return openingPhase;
        }
        if (healthFraction > 0.33) {
            return middlePhase;
        }
        return finalPhase;
    }

    public String label() {
        return label;
    }

    public BossArt art() {
        return art;
    }

    public int health() {
        return health;
    }

    public int scoreValue() {
        return scoreValue;
    }
}
