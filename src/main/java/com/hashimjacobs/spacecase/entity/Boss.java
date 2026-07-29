package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.BossArt;

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
            BossPhase.SWEEPING_FAN, BossPhase.SPIRAL, BossPhase.RING);

    private final String label;
    private final BossArt art;
    private final int health;
    private final int scoreValue;
    private final BossPhase openingPhase;
    private final BossPhase middlePhase;
    private final BossPhase finalPhase;

    Boss(String label, BossArt art, int health, int scoreValue,
         BossPhase openingPhase, BossPhase middlePhase, BossPhase finalPhase) {
        this.label = label;
        this.art = art;
        this.health = health;
        this.scoreValue = scoreValue;
        this.openingPhase = openingPhase;
        this.middlePhase = middlePhase;
        this.finalPhase = finalPhase;
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
