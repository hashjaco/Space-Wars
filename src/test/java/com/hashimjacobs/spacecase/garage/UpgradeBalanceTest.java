package com.hashimjacobs.spacecase.garage;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ways the upgrade numbers can quietly break the game.
 *
 * None of these would fail a compile or throw at runtime -- each just makes something behave
 * backwards, which is exactly the kind of thing that survives a playtest unnoticed and turns up
 * months later as "the speed pickup feels wrong".
 *
 * Every track that improves a pickup gets one here, because those are the dangerous ones: they
 * compound with something the player already earned, and the failure mode is a bought advantage
 * quietly replacing a found one.
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

    /**
     * The whole catalogue outlasts one galaxy's pay.
     *
     * Measured against the real prices rather than one track multiplied by the enum size, which is
     * what this used to do -- with the tracks priced apart that arithmetic stopped describing
     * anything, and grew more true every time an upgrade was added, which is the opposite of a
     * useful assertion.
     *
     * A clean ten-level galaxy pays roughly 150 credits a level; see {@code mode.Debrief.credits}.
     */
    @Test
    void theCatalogueOutlastsOneGalaxy() {
        int everything = Upgrade.catalogueCost();
        int oneGalaxy = 150 * 10;

        assertTrue(everything > oneGalaxy * 2,
                "the whole catalogue at " + everything + " credits would be bought in two galaxies");
    }

    /** A shortened reload must not turn the rocket pickup into the primary weapon. */
    @Test
    void theSalvoRackNeverOutpacesTheGun() {
        int rocket = Upgrade.rocketCooldownAt(Upgrade.SALVO.maxLevel());
        int gun = GameConfig.PLAYER_FIRE_COOLDOWN_FLOOR;

        assertTrue(rocket > gun * 2,
                "rockets every " + rocket + " ticks against a gun every " + gun
                        + " would make the pickup the weapon you fly with");
        assertTrue(rocket >= GameConfig.ROCKET_FIRE_COOLDOWN_FLOOR,
                "the floor would silently swallow the last level of the rack");
    }

    /**
     * The repair rig cannot outheal being shot.
     *
     * Otherwise the right way to play a hard level is to sit in the open and let it tick, which is
     * the opposite of what a reward for clean flying should encourage.
     */
    @Test
    void theRepairRigCannotOuthealEnemyFire() {
        int level = Upgrade.REPAIR.maxLevel();
        double healedPerSecond = 60.0 * level / GameConfig.REPAIR_INTERVAL_TICKS;

        assertTrue(healedPerSecond < GameConfig.ENEMY_BULLET_DAMAGE,
                "repairing " + healedPerSecond + " health a second against "
                        + GameConfig.ENEMY_BULLET_DAMAGE + " a hit would remove the cost of a hit");
        assertTrue(GameConfig.REPAIR_CALM_TICKS > 60,
                "the calm window has to be long enough that a firefight never counts as calm");
    }

    /** The collector should save a detour, not fetch the arena. */
    @Test
    void theCollectorNeverOutrunsTheShip() {
        assertTrue(GameConfig.UPGRADE_COLLECTOR_PULL < GameConfig.PLAYER_SPEED,
                "a pull faster than the ship would mean pickups arrive whether you fly to them");
    }

    /** Eject gear buys a moment to get clear, not a free pass through a boss phase. */
    @Test
    void ejectGearIsNotAFreeBossPhase() {
        int grace = GameConfig.PLAYER_INVULNERABLE_TICKS
                + Upgrade.EJECT.maxLevel() * GameConfig.UPGRADE_EJECT_STEP;
        double crossArenaTicks = GameConfig.HEIGHT / GameConfig.PLAYER_SPEED;

        assertTrue(grace < crossArenaTicks * 2,
                grace + " ticks of invulnerability is long enough to fly the arena twice over");
    }

    /**
     * A maxed capacitor is still worth less than the hull it protects.
     *
     * The shield is meant to be a reprieve. Once one pickup soaks more than a full health bar, the
     * hull upgrades stop mattering and the game becomes about holding a shield at all times.
     */
    @Test
    void aShieldNeverSoaksMoreThanAFullHull() {
        int shield = GameConfig.SHIELD_CAPACITY
                + Upgrade.CAPACITOR.maxLevel() * GameConfig.UPGRADE_CAPACITOR_STEP;

        assertTrue(shield < GameConfig.PLAYER_HEALTH,
                "a shield soaking " + shield + " against a " + GameConfig.PLAYER_HEALTH
                        + " health hull would be better than the hull");
    }

    /** Every track has to be reachable, priced, and describable. */
    @Test
    void everyUpgradeIsWellFormed() {
        for (Upgrade upgrade : Upgrade.values()) {
            assertTrue(upgrade.maxLevel() >= 1, upgrade + " cannot be bought at all");
            assertTrue(upgrade.costFor(0) > 0, upgrade + " is free");
            assertTrue(upgrade.costFor(upgrade.maxLevel()) == 0, upgrade + " sells past its maximum");
            assertTrue(upgrade.description() != null && !upgrade.description().isBlank(),
                    upgrade + " needs a description for its row");
            assertTrue(upgrade.category() != null, upgrade + " needs a category");
            for (int level = 0; level <= upgrade.maxLevel(); level++) {
                String effect = upgrade.effectAt(level);
                assertTrue(effect != null && !effect.isBlank(),
                        upgrade + " cannot describe what it does at level " + level);
            }
        }
    }
}
