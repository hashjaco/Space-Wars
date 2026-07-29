package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static void runTicks(PlayerShip ship, int count) {
        for (int i = 0; i < count; i++) {
            ship.tickTimers();
        }
    }
}
