package com.hashimjacobs.spacecase.mode;

/**
 * What shape a level is, beyond its palette.
 *
 * Until this existed the only structural difference between levels was which way they ran, so every
 * level was the same open lane with a different sky. A template adds the rest: rock closing in from
 * both sides, something pulling the ship down-arena, somewhere too hot to sit still in.
 *
 * A theme is a row here, not a code path. That is the whole point -- a cave, a canyon, an ice tunnel
 * and a lava fissure differ in four numbers, and adding the fifth costs a line rather than a class.
 * Only the two that are actually flown are defined; the others go in as the level that needs them is
 * built and tuned, because an untuned row is worse than no row.
 *
 * Colours are hex strings rather than {@code javafx.scene.paint.Color} so this stays usable in tests
 * that never start the toolkit, the same bargain {@link Galaxy#accent()} makes.
 */
public enum WorldTemplate {

    /** Open space. Every level flew like this before templates existed, and most still do. */
    OPEN_FIELD(1.00, 0, 0, 0, 0, 0, null, null, "Open space"),

    /**
     * A tunnel: rock closing in from both sides, and waves still coming at you down the middle.
     *
     * The numbers were picked by drawing them rather than by reasoning about them. Nine harmonics
     * with a slow falloff put lobes and throats on the walls instead of a smooth taper; the lane
     * runs about 56% of the arena on average, pinching to around 45% at the throats, against a
     * guaranteed floor of 34%. A ship is 64px wide, so even the worst pinch the arithmetic permits
     * leaves five ship-widths -- tight enough to have to steer through, never tight enough to be
     * stuck in. Contact hurts about as much as ramming an enemy.
     */
    CAVE(0.34, 0.60, 9, 14, 0, 0, "#3a2f27", "#7a6250", "Rock closes in -- mind the walls"),

    /**
     * The Forgeworks: the cave, with heat on top. Two a second is about a third of a full hull a
     * minute, so lingering costs something but a clean run through the waves does not.
     */
    FORGE(0.34, 0.60, 9, 14, 2, 0, "#3a2420", "#c2552a", "Furnace heat -- the hull cooks while you linger"),

    /**
     * Coronal Arc: open space with the star dragging every ship toward the bottom of the arena.
     * 0.9 against a PLAYER_SPEED of 5.5 is felt on every dodge but always beaten.
     */
    SUN_DRAG(1.00, 0, 0, 0, 0, 0.9, null, null, "Solar wind -- the star drags you downrange");

    private final double minLaneFraction;
    private final double roughness;
    private final int harmonics;
    private final int contactDamage;
    private final int ambientDamage;
    private final double pull;
    private final String rockHex;
    private final String edgeHex;
    private final String hint;

    WorldTemplate(double minLaneFraction, double roughness, int harmonics, int contactDamage,
                  int ambientDamage, double pull, String rockHex, String edgeHex, String hint) {
        this.minLaneFraction = minLaneFraction;
        this.roughness = roughness;
        this.harmonics = harmonics;
        this.contactDamage = contactDamage;
        this.ambientDamage = ambientDamage;
        this.pull = pull;
        this.rockHex = rockHex;
        this.edgeHex = edgeHex;
        this.hint = hint;
    }

    /** One line for the route map, so the hazard is known before it is flown into. */
    public String hint() {
        return hint;
    }

    /**
     * The narrowest the open lane is ever allowed to get, as a fraction of the arena's width.
     *
     * A guarantee rather than a target: {@code engine.Terrain} derives its rock from this so the
     * lane mathematically cannot close below it, whatever the roughness. That is what makes an
     * unwinnable pinch impossible rather than merely unlikely.
     */
    public double minLaneFraction() {
        return minLaneFraction;
    }

    /** How much of its allowance the rock actually swings through, 0 to 1. */
    public double roughness() {
        return roughness;
    }

    /** Terms in the harmonic sum: how spiky the surface is. More means finer detail. */
    public int harmonics() {
        return harmonics;
    }

    /** Damage for scraping the rock, on the same grace period as ramming an enemy. */
    public int contactDamage() {
        return contactDamage;
    }

    /** Damage a second simply for being here -- heat, cold, pressure. Zero in most levels. */
    public int ambientDamage() {
        return ambientDamage;
    }

    /**
     * Pixels per tick the ship is dragged down-arena: an undertow, a gravity well, a solar wind.
     *
     * Has to stay well under {@code GameConfig.PLAYER_SPEED} or the ship cannot make headway
     * against it, which stops being a hazard and starts being a level you watch.
     */
    public double pull() {
        return pull;
    }

    /** Rock fill, or null when there is no rock. Parsed by the renderer. */
    public String rockHex() {
        return rockHex;
    }

    /** The lit edge of the rock -- the line the player reads as the thing that will hurt them. */
    public String edgeHex() {
        return edgeHex;
    }

    /** Whether this template has any rock at all. */
    public boolean hasRock() {
        return minLaneFraction < 1;
    }
}
