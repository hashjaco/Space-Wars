package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.garage.Kit;
import com.hashimjacobs.spacecase.garage.Livery;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.garage.Upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShipTest {

    @Test
    void losingAllHealthCostsALifeAndRespawnsAtTheStart() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 400, 700);
        ship.setPosition(120, 200);

        boolean lostALife = ship.takeDamage(GameConfig.PLAYER_HEALTH);

        assertTrue(lostALife);
        assertEquals(GameConfig.PLAYER_LIVES - 1, ship.lives());
        assertEquals(GameConfig.PLAYER_HEALTH, ship.health(), "respawn restores full health");
        assertEquals(400, ship.x());
        assertEquals(700, ship.y());
    }

    @Test
    void aShipIsOutOnceItsLivesAreGone() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        for (int i = 0; i < GameConfig.PLAYER_LIVES; i++) {
            ship.takeDamage(GameConfig.PLAYER_HEALTH);
            // Clear the respawn grace period so the next hit lands.
            runTicks(ship, GameConfig.PLAYER_INVULNERABLE_TICKS + 1);
        }
        assertTrue(ship.isOut());
    }

    @Test
    void respawnGracePeriodBlocksDamage() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        ship.takeDamage(GameConfig.PLAYER_HEALTH);
        assertTrue(ship.isInvulnerable());

        int livesAfterDeath = ship.lives();
        ship.takeDamage(GameConfig.PLAYER_HEALTH);

        assertEquals(livesAfterDeath, ship.lives(), "hits during the grace period are ignored");
    }

    @Test
    void shieldAbsorbsDamage() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        runTicks(ship, GameConfig.PLAYER_INVULNERABLE_TICKS + 1);
        ship.collect(PowerUp.Kind.SHIELD);

        ship.takeDamage(50);

        assertEquals(GameConfig.PLAYER_HEALTH, ship.health());
    }

    @Test
    void healthPickupRefillsAndExtraLifeAdds() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        runTicks(ship, GameConfig.PLAYER_INVULNERABLE_TICKS + 1);
        ship.takeDamage(40);
        assertEquals(GameConfig.PLAYER_HEALTH - 40, ship.health());

        ship.collect(PowerUp.Kind.HEALTH);
        assertEquals(GameConfig.PLAYER_HEALTH, ship.health());

        int lives = ship.lives();
        ship.collect(PowerUp.Kind.EXTRA_LIFE);
        assertEquals(lives + 1, ship.lives());
    }

    @Test
    void timedEffectsExpire() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        ship.collect(PowerUp.Kind.TRI_SHOT);
        assertTrue(ship.hasEffect(PowerUp.Kind.TRI_SHOT));

        runTicks(ship, GameConfig.POWERUP_DURATION_TICKS + 1);

        assertFalse(ship.hasEffect(PowerUp.Kind.TRI_SHOT));
    }

    @Test
    void speedPickupRaisesTopSpeed() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        assertEquals(GameConfig.PLAYER_SPEED, ship.speed());

        ship.collect(PowerUp.Kind.SPEED);

        assertEquals(GameConfig.PLAYER_SPEED_BOOSTED, ship.speed());
    }

    @Test
    void fireCooldownGatesShots() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        assertTrue(ship.canFire());

        ship.startFireCooldown();
        assertFalse(ship.canFire());

        runTicks(ship, GameConfig.PLAYER_FIRE_COOLDOWN);
        assertTrue(ship.canFire());
    }

    /**
     * The pose lookup is indexed by {@code Lean.ordinal()}, so a reordered enum or a mis-ordered pose
     * list silently swaps the ship's banking. It shipped backwards once already.
     */
    @Test
    void eachLeanPicksTheSpriteThatBanksThatWay() {
        PlayerShip first = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);

        first.setLean(PlayerShip.Lean.HARD_LEFT);
        assertEquals(Sprite.P1_BANK_LEFT, first.sprite());
        first.setLean(PlayerShip.Lean.LEFT);
        assertEquals(Sprite.P1_LEFT, first.sprite());
        first.setLean(PlayerShip.Lean.NONE);
        assertEquals(Sprite.P1_STRAIGHT, first.sprite());
        first.setLean(PlayerShip.Lean.RIGHT);
        assertEquals(Sprite.P1_RIGHT, first.sprite());
        first.setLean(PlayerShip.Lean.HARD_RIGHT);
        assertEquals(Sprite.P1_BANK_RIGHT, first.sprite());

        PlayerShip second = new PlayerShip(2, "TESTER", Facing.UP, 0, 0);
        second.setLean(PlayerShip.Lean.HARD_RIGHT);
        assertEquals(Sprite.P2_BANK_RIGHT, second.sprite(), "player two must bank the same way");
    }

    @Test
    void takingAHitSwapsToTheScorchedFrameOfTheSamePose() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        ship.setLean(PlayerShip.Lean.HARD_RIGHT);
        assertEquals(Sprite.P1_BANK_RIGHT, ship.sprite());

        ship.takeDamage(10);
        ship.refreshSprite();
        assertEquals(Sprite.P1_BANK_RIGHT_HIT, ship.sprite(),
                "a hit must scorch the current pose, not reset the bank");
    }

    @Test
    void reviveRestoresLivesHealthAndTheSpawnPoint() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 400, 700);
        ship.setPosition(50, 50);
        for (int i = 0; i < GameConfig.PLAYER_LIVES; i++) {
            ship.takeDamage(GameConfig.PLAYER_HEALTH);
            runTicks(ship, GameConfig.PLAYER_INVULNERABLE_TICKS + 1);
        }
        assertTrue(ship.isOut());

        ship.revive();

        assertFalse(ship.isOut());
        assertEquals(GameConfig.PLAYER_LIVES, ship.lives());
        assertEquals(ship.maxHealth(), ship.health());
        assertEquals(400, ship.x());
        assertEquals(700, ship.y());
        assertTrue(ship.isInvulnerable());
    }

    @Test
    void platingReducesEveryHitButNeverToNothing() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        Loadout plated = Loadout.stock(1);
        for (int i = 0; i < GameConfig.UPGRADE_MAX_LEVEL; i++) {
            plated.raise(Upgrade.SHIELDING);
        }
        ship.applyLoadout(plated);
        runTicks(ship, GameConfig.PLAYER_INVULNERABLE_TICKS + 1);

        int absorbed = GameConfig.UPGRADE_MAX_LEVEL * GameConfig.UPGRADE_SHIELD_STEP;
        ship.takeDamage(20);
        assertEquals(ship.maxHealth() - (20 - absorbed), ship.health());

        int before = ship.health();
        ship.takeDamage(1);
        assertEquals(before - 1, ship.health(), "a hit must always cost at least one point");
    }

    @Test
    void aHullUpgradeRaisesTheCeilingAndRespawnRefillsToIt() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        Loadout reinforced = Loadout.stock(1);
        reinforced.raise(Upgrade.HULL);
        reinforced.raise(Upgrade.HULL);
        ship.applyLoadout(reinforced);

        int expected = GameConfig.PLAYER_HEALTH + 2 * GameConfig.UPGRADE_HULL_STEP;
        assertEquals(expected, ship.maxHealth());

        // A fresh hull does not heal on purchase; the extra points arrive on the next respawn.
        ship.takeDamage(GameConfig.PLAYER_HEALTH);
        assertEquals(expected, ship.health(), "respawn should refill to the raised ceiling");
    }

    @Test
    void firepowerScalesBothTheStandardAndTheMegaShot() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        assertEquals(GameConfig.BULLET_DAMAGE, ship.damageFor(GameConfig.BULLET_DAMAGE));

        Loadout armed = Loadout.stock(1);
        armed.raise(Upgrade.FIREPOWER);
        armed.raise(Upgrade.FIREPOWER);
        ship.applyLoadout(armed);

        assertTrue(ship.damageFor(GameConfig.BULLET_DAMAGE) > GameConfig.BULLET_DAMAGE);
        assertTrue(ship.damageFor(GameConfig.MEGA_BULLET_DAMAGE) > GameConfig.MEGA_BULLET_DAMAGE);
    }

    @Test
    void thrustersRaiseSpeedWithoutOvertakingTheSpeedPickup() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        double stock = ship.speed();

        Loadout tuned = Loadout.stock(1);
        for (int i = 0; i < GameConfig.UPGRADE_MAX_LEVEL; i++) {
            tuned.raise(Upgrade.SPEED);
        }
        ship.applyLoadout(tuned);

        assertTrue(ship.speed() > stock);
        assertTrue(ship.speed() < GameConfig.PLAYER_SPEED_BOOSTED,
                "collecting the speed pickup must still be an upgrade");
    }

    @Test
    void aPaintJobChangesEveryPoseIncludingTheScorchedOne() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        Loadout painted = Loadout.stock(1);
        painted.unlock(Livery.VOID_VIOLET);
        ship.applyLoadout(painted);

        ship.setLean(PlayerShip.Lean.HARD_RIGHT);
        assertEquals(Sprite.VIOLET_BANK_RIGHT, ship.sprite());

        ship.takeDamage(10);
        ship.refreshSprite();
        assertEquals(Sprite.VIOLET_BANK_RIGHT_HIT, ship.sprite());
    }

    @Test
    void aBodyKitTracksThePoseAndStockHasNone() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        assertNull(ship.kitOverlay(), "a stock ship carries no decal");

        Loadout kitted = Loadout.stock(1);
        kitted.unlock(Kit.FINS);
        ship.applyLoadout(kitted);

        ship.setLean(PlayerShip.Lean.HARD_LEFT);
        assertEquals(Sprite.KIT_FINS_BANK_LEFT, ship.kitOverlay());
        ship.setLean(PlayerShip.Lean.NONE);
        assertEquals(Sprite.KIT_FINS_STRAIGHT, ship.kitOverlay());
    }

    @Test
    void progressRoundTripsEveryCounter() {
        PlayerShip flown = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        flown.addScore(4200);
        flown.recordEnemyKill();
        flown.recordAsteroidKill();
        flown.recordShot();
        flown.recordHit();
        flown.takeDamage(30);

        PlayerShip fresh = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);
        fresh.restore(flown.progress());

        assertEquals(flown.score(), fresh.score());
        assertEquals(flown.lives(), fresh.lives());
        assertEquals(flown.health(), fresh.health());
        assertEquals(flown.enemiesKilled(), fresh.enemiesKilled());
        assertEquals(flown.asteroidsDestroyed(), fresh.asteroidsDestroyed());
        assertEquals(flown.shotsFired(), fresh.shotsFired());
        assertEquals(flown.shotsHit(), fresh.shotsHit());
        assertEquals(flown.damageTaken(), fresh.damageTaken());
    }

    /** A save can predate a hull upgrade, or postdate one since reloaded onto another pilot. */
    @Test
    void restoringHealthClampsToThisShipsCeiling() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);

        ship.restore(new PlayerShip.Progress(99999, 3, 0, 0, 0, 0, 0, 0));

        assertEquals(ship.maxHealth(), ship.health());
    }

    /** A checkpoint of a ship with no lives is a record of a round that had already ended. */
    @Test
    void restoringZeroLivesFloorsAtOne() {
        PlayerShip ship = new PlayerShip(1, "TESTER", Facing.UP, 0, 0);

        ship.restore(new PlayerShip.Progress(50, 0, 0, 0, 0, 0, 0, 0));

        assertEquals(1, ship.lives());
        assertFalse(ship.isOut());
    }

    private static void runTicks(PlayerShip ship, int count) {
        for (int i = 0; i < count; i++) {
            ship.tickTimers();
        }
    }
}
