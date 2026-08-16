package com.hashimjacobs.spacecase.garage;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * What a pilot can buy for their ship between levels.
 *
 * Each track runs from level 0 to {@link GameConfig#UPGRADE_MAX_LEVEL}, and each step costs more
 * than the last so a maxed track is a real commitment rather than a formality. What a level is
 * worth lives with the thing it changes -- {@code entity.PlayerShip} for speed, plating and hull,
 * {@code engine.ShipController} for firepower -- and only the price lives here.
 */
public enum Upgrade {

    FIREPOWER("Firepower"),
    FIRE_RATE("Fire rate"),
    SPEED("Thrusters"),
    SHIELDING("Plating"),
    HULL("Hull");

    private final String label;

    Upgrade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Credits to go from {@code level} to the next one, or 0 when the track is already maxed.
     *
     * The same curve for every track: they are all worth roughly the same, so pricing them apart
     * would only be a balance lever nobody asked for.
     */
    public static int costFor(int level) {
        if (level >= GameConfig.UPGRADE_MAX_LEVEL) {
            return 0;
        }
        int cost = GameConfig.UPGRADE_COST_STEP * (level + 1);
        return cost;
    }
}
