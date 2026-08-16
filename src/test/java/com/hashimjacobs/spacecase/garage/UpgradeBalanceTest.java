package com.hashimjacobs.spacecase.garage;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three ways the upgrade numbers can quietly break the game.
 *
 * None of these would fail a compile or throw at runtime -- each just makes something in the game
 * behave backwards, which is exactly the kind of thing that survives a playtest unnoticed and turns
 * up months later as "the speed pickup feels wrong".
 */
class UpgradeBalanceTest {

    @Test
    void aMaxedShipIsStillSlowerThanTheSpeedPickupMakesIt() {
        double maxed = GameConfig.PLAYER_SPEED
                + GameConfig.UPGRADE_MAX_LEVEL * GameConfig.UPGRADE_SPEED_STEP;

        assertTrue(maxed < GameConfig.PLAYER_SPEED_BOOSTED,
                "thrusters at " + maxed + " would make the speed pickup ("
                        + GameConfig.PLAYER_SPEED_BOOSTED + ") a downgrade");
    }

    @Test
    void theFireRateUpgradeCannotOutrunItsFloor() {
        int fastest = GameConfig.PLAYER_FIRE_COOLDOWN - GameConfig.UPGRADE_MAX_LEVEL;

        assertTrue(fastest >= GameConfig.PLAYER_FIRE_COOLDOWN_FLOOR,
                "the floor would silently swallow the last levels of the fire-rate track");
    }

    @Test
    void platingNeverMakesTheWeakestShotHarmless() {
        int absorbed = GameConfig.UPGRADE_MAX_LEVEL * GameConfig.UPGRADE_SHIELD_STEP;

        assertTrue(GameConfig.ENEMY_BULLET_DAMAGE - absorbed >= 1,
                "a maxed hull would be immune to ordinary enemy fire");
    }

    @Test
    void maxingEverythingCostsMoreThanASingleRunPays() {
        int perTrack = 0;
        for (int level = 0; level < GameConfig.UPGRADE_MAX_LEVEL; level++) {
            perTrack += Upgrade.costFor(level);
        }
        int everything = perTrack * Upgrade.values().length;

        // A generous eight-level run pays a few hundred credits; see Debrief.credits.
        assertTrue(everything > 1000,
                "the whole catalogue at " + everything + " credits would be bought in one run");
    }
}
