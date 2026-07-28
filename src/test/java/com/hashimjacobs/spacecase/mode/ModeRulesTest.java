package com.hashimjacobs.spacecase.mode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.engine.World;
import com.hashimjacobs.spacecase.entity.PlayerShip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeRulesTest {

    @Test
    void soloHasOnePlayerAndNoFriendlyFire() {
        ModeRules rules = GameMode.SOLO.rules();
        assertEquals(1, rules.playerCount());
        assertFalse(rules.friendlyFire());
        assertTrue(rules.spawnEnemies());
    }

    @Test
    void coopHasTwoPlayersWhoCannotShootEachOther() {
        ModeRules rules = GameMode.COOP.rules();
        assertEquals(2, rules.playerCount());
        assertFalse(rules.friendlyFire());
        assertFalse(rules.lastPlayerStanding());
    }

    @Test
    void battleEnablesFriendlyFireAndDropsTheAiEnemies() {
        ModeRules rules = GameMode.BATTLE.rules();
        assertEquals(2, rules.playerCount());
        assertTrue(rules.friendlyFire(), "battle mode is player versus player");
        assertFalse(rules.spawnEnemies(), "battle mode is a duel, not a horde");
        assertTrue(rules.spawnAsteroids(), "asteroids remain as shared hazards");
        assertTrue(rules.spawnPowerUps(), "pickups are contested");
        assertTrue(rules.lastPlayerStanding());
    }

    @Test
    void battleSpawnsTheSecondPlayerFacingTheFirst() {
        World world = new World(GameMode.BATTLE);
        PlayerShip one = world.players().get(0);
        PlayerShip two = world.players().get(1);

        assertEquals(Facing.UP, one.facing());
        assertEquals(Facing.DOWN, two.facing());
        assertTrue(two.y() < one.y(), "player two starts above player one");
    }

    @Test
    void coopSpawnsBothPlayersFacingTheSameWay() {
        World world = new World(GameMode.COOP);
        PlayerShip one = world.players().get(0);
        PlayerShip two = world.players().get(1);

        assertEquals(Facing.UP, one.facing());
        assertEquals(Facing.UP, two.facing());
        assertNotEquals(one.x(), two.x(), "co-op players start side by side");
    }

    @Test
    void facingDrivesBulletDirectionAndSpriteRotation() {
        assertEquals(-1, Facing.UP.yDirection());
        assertEquals(1, Facing.DOWN.yDirection());
        assertEquals(0, Facing.UP.rotationDegrees());
        assertEquals(180, Facing.DOWN.rotationDegrees());
    }

    @Test
    void battleModeUsesItsOwnMusic() {
        assertNotEquals(GameMode.SOLO.music(), GameMode.BATTLE.music());
    }
}
