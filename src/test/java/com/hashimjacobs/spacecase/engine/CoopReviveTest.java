package com.hashimjacobs.spacecase.engine;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoopReviveTest {

    /** Empties a ship's lives the way a level does, waiting out each respawn grace period. */
    private static void knockOut(PlayerShip ship) {
        while (!ship.isOut()) {
            ship.takeDamage(GameConfig.PLAYER_HEALTH);
            for (int tick = 0; tick <= GameConfig.PLAYER_INVULNERABLE_TICKS; tick++) {
                ship.tickTimers();
            }
        }
    }

    @Test
    void aPartnerWhoClearsTheLevelBuysBackTheOneWhoDied() {
        World world = new World(GameMode.COOP);
        PlayerShip second = world.players().get(1);
        knockOut(second);
        assertTrue(second.isOut());

        world.reviveFallenAllies();

        assertFalse(second.isOut());
        assertEquals(GameConfig.PLAYER_LIVES, second.lives());
        assertEquals(second.maxHealth(), second.health());
        assertTrue(second.isInvulnerable(), "a revived ship gets the usual grace period");
    }

    @Test
    void aPilotWhoIsStillFlyingIsLeftAlone() {
        World world = new World(GameMode.COOP);
        PlayerShip first = world.players().get(0);
        first.takeDamage(GameConfig.PLAYER_HEALTH);
        int livesAfterOneDeath = first.lives();

        world.reviveFallenAllies();

        assertEquals(livesAfterOneDeath, first.lives(),
                "reviving must not top up someone who still has lives");
    }

    /** Outlasting the other player is how battle mode is won; reviving them would undo the win. */
    @Test
    void battleModeNeverRevives() {
        World world = new World(GameMode.BATTLE);
        PlayerShip second = world.players().get(1);
        knockOut(second);

        world.reviveFallenAllies();

        assertTrue(second.isOut());
    }

    /**
     * A level can be cleared on the very tick the last life is lost, because the loop checks for a
     * cleared level before it checks for a finished round. Without the player-count guard that
     * would hand a solo player three free lives instead of a game over.
     */
    @Test
    void soloNeverRevives() {
        World world = new World(GameMode.SOLO);
        PlayerShip solo = world.players().get(0);
        knockOut(solo);

        world.reviveFallenAllies();

        assertTrue(solo.isOut());
    }
}
