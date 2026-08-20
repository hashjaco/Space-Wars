package com.hashimjacobs.spacecase.garage;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * What a pilot can buy for their ship.
 *
 * The effects deliberately live elsewhere -- in {@code entity.PlayerShip} and
 * {@code engine.ShipController}, next to the thing each one changes -- so this stays a catalogue
 * rather than a second copy of the combat rules. What lives here is what the garage needs to draw a
 * row and charge for it.
 *
 * <p><strong>Nothing here grants a weapon.</strong> The beam, the rockets and the tri-shot come from
 * pickups, are lost when you die, and are the reward for flying well in the moment. The garage sells
 * the ship: the gun it always has, the hull, the thrusters -- and how much better it makes the things
 * you find. That split is what keeps a bought advantage from replacing a found one.
 *
 * @see Kit for why the cosmetics carry no stats
 */
public enum Upgrade {

    // ---- Weapons ------------------------------------------------------------------------------

    FIREPOWER("Firepower", "Every shot hits harder.",
            Category.WEAPONS, GameConfig.UPGRADE_MAX_LEVEL, GameConfig.UPGRADE_COST_STEP),
    FIRE_RATE("Fire rate", "Less time between shots.",
            Category.WEAPONS, GameConfig.UPGRADE_MAX_LEVEL, GameConfig.UPGRADE_COST_STEP),
    SALVO("Salvo rack", "Rockets you pick up reload faster.",
            Category.WEAPONS, 2, 220),
    FOCUS("Focusing coil", "The mega laser burns hotter.",
            Category.WEAPONS, 2, 240),

    // ---- Defence ------------------------------------------------------------------------------

    HULL("Hull", "More maximum health.",
            Category.DEFENCE, GameConfig.UPGRADE_MAX_LEVEL, GameConfig.UPGRADE_COST_STEP),
    SHIELDING("Plating", "Every hit lands for less.",
            Category.DEFENCE, GameConfig.UPGRADE_MAX_LEVEL, GameConfig.UPGRADE_COST_STEP),
    CAPACITOR("Capacitor", "Shields you pick up soak more.",
            Category.DEFENCE, 2, 140),
    REPAIR("Repair rig", "Patches the hull while you fly clean.",
            Category.DEFENCE, 3, 140),

    // ---- Mobility -----------------------------------------------------------------------------

    SPEED("Thrusters", "A faster ship.",
            Category.MOBILITY, GameConfig.UPGRADE_MAX_LEVEL, GameConfig.UPGRADE_COST_STEP),
    EJECT("Eject gear", "Longer grace period after a respawn.",
            Category.MOBILITY, 2, 120),

    // ---- Utility ------------------------------------------------------------------------------

    COLLECTOR("Collector", "Pickups drift toward you.",
            Category.UTILITY, 2, 100);

    /** How the garage groups the rows. Order here is the order they are listed in. */
    public enum Category {
        WEAPONS("Weapons"), DEFENCE("Defence"), MOBILITY("Mobility"), UTILITY("Utility");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String label;
    private final String description;
    private final Category category;
    private final int maxLevel;
    private final int costBase;

    Upgrade(String label, String description, Category category, int maxLevel, int costBase) {
        this.label = label;
        this.description = description;
        this.category = category;
        this.maxLevel = maxLevel;
        this.costBase = costBase;
    }

    public String label() {
        return label;
    }

    /** One line for the garage row, so a pilot can tell what they are buying. */
    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    /**
     * Levels this track has.
     *
     * Per-upgrade rather than one global maximum, because the tracks are not the same shape: the
     * four original stat ladders want four small steps, while something that shortens a reload wants
     * two large ones. {@code GameConfig.UPGRADE_MAX_LEVEL} stays as the value the originals use.
     */
    public int maxLevel() {
        return maxLevel;
    }

    /**
     * Credits to go from {@code level} to the next one, or 0 when the track is already maxed.
     *
     * The curve is the same shape for every track -- each step costs one more multiple of the base
     * than the last -- but the base differs. The four originals are a hundred credits a step at the
     * bottom; the ones that improve a pickup cost more, because they compound with something the
     * player already had to earn.
     */
    public int costFor(int level) {
        if (level >= maxLevel) {
            return 0;
        }
        return costBase * (level + 1);
    }

    /** Everything this track costs from nothing to maxed. */
    public int totalCost() {
        int total = 0;
        for (int level = 0; level < maxLevel; level++) {
            total += costFor(level);
        }
        return total;
    }

    /** What the whole catalogue costs. What the garage economy has to be measured against. */
    public static int catalogueCost() {
        int total = 0;
        for (Upgrade upgrade : values()) {
            total += upgrade.totalCost();
        }
        return total;
    }

    /**
     * What this track does at a given level, in the units the player cares about.
     *
     * Drawn as "damage 12 -> 14" on the focused row, so buying is an informed decision rather than
     * a shrug at a bar filling up. Pure arithmetic over {@code GameConfig}, so it is unit-testable
     * and cannot drift from the effect as long as both read the same constants.
     */
    public String effectAt(int level) {
        return switch (this) {
            case FIREPOWER -> "damage " + Math.round(GameConfig.BULLET_DAMAGE
                    * (1 + level * GameConfig.UPGRADE_DAMAGE_STEP));
            case FIRE_RATE -> "every " + Math.max(GameConfig.PLAYER_FIRE_COOLDOWN_FLOOR,
                    GameConfig.PLAYER_FIRE_COOLDOWN - level) + " ticks";
            case SALVO -> "rocket every " + rocketCooldownAt(level) + " ticks";
            case FOCUS -> "beam " + (GameConfig.BEAM_DAMAGE_PER_TICK
                    + level * GameConfig.UPGRADE_BEAM_STEP) + " a tick";
            case HULL -> (GameConfig.PLAYER_HEALTH + level * GameConfig.UPGRADE_HULL_STEP) + " health";
            case SHIELDING -> "-" + (level * GameConfig.UPGRADE_SHIELD_STEP) + " a hit";
            case CAPACITOR -> "shield soaks "
                    + (GameConfig.SHIELD_CAPACITY + level * GameConfig.UPGRADE_CAPACITOR_STEP);
            case REPAIR -> level == 0 ? "none" : "1 health every "
                    + (GameConfig.REPAIR_INTERVAL_TICKS / level / 60) + "s";
            case SPEED -> String.format("%.2f speed",
                    GameConfig.PLAYER_SPEED + level * GameConfig.UPGRADE_SPEED_STEP);
            case EJECT -> ((GameConfig.PLAYER_INVULNERABLE_TICKS
                    + level * GameConfig.UPGRADE_EJECT_STEP) / 60.0) + "s of grace";
            case COLLECTOR -> level == 0 ? "none"
                    : (level * GameConfig.UPGRADE_COLLECTOR_RANGE) + "px reach";
        };
    }

    /** Shared by {@link #effectAt} and the controller, so the readout cannot lie about the reload. */
    public static int rocketCooldownAt(int salvoLevel) {
        int shortened = GameConfig.ROCKET_FIRE_COOLDOWN
                - salvoLevel * GameConfig.UPGRADE_SALVO_STEP;
        return Math.max(GameConfig.ROCKET_FIRE_COOLDOWN_FLOOR, shortened);
    }
}
