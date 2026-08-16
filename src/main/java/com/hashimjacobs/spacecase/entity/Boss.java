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
            3, BossArt.HYDRA_HEAD);

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
