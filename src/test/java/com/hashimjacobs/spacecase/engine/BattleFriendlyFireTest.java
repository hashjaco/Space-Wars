package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Battle mode is the only mode where one player's fire may harm the other. */
class BattleFriendlyFireTest {

    @Test
    void aBulletFromPlayerOneDamagesPlayerTwoInBattle() {
        World world = new World(GameMode.BATTLE);
        PlayerShip shooter = world.players().get(0);
        PlayerShip victim = world.players().get(1);
        clearGracePeriod(victim);

        world.addBullet(bulletOnTopOf(victim, shooter));
        new CollisionSystem(SoundPlayer.SILENT).resolve(world);

        assertTrue(victim.health() < 100, "friendly fire should land in battle mode");
    }

    @Test
    void theSameShotDoesNothingInCoop() {
        World world = new World(GameMode.COOP);
        PlayerShip shooter = world.players().get(0);
        PlayerShip teammate = world.players().get(1);
        clearGracePeriod(teammate);

        world.addBullet(bulletOnTopOf(teammate, shooter));
        new CollisionSystem(SoundPlayer.SILENT).resolve(world);

        assertEquals(100, teammate.health(), "co-op teammates cannot shoot each other");
    }

    @Test
    void aPlayerCannotShootThemself() {
        World world = new World(GameMode.BATTLE);
        PlayerShip shooter = world.players().get(0);
        clearGracePeriod(shooter);

        world.addBullet(bulletOnTopOf(shooter, shooter));
        new CollisionSystem(SoundPlayer.SILENT).resolve(world);

        assertEquals(100, shooter.health(), "a bullet must not hit its own owner");
    }

    @Test
    void enemyFireHurtsPlayersInEveryMode() {
        World world = new World(GameMode.COOP);
        PlayerShip victim = world.players().get(0);
        clearGracePeriod(victim);

        world.addBullet(bulletOnTopOf(victim, null));
        new CollisionSystem(SoundPlayer.SILENT).resolve(world);

        assertTrue(victim.health() < 100);
    }

    @Test
    void battleRoundEndsWhenOnlyOnePlayerRemains() {
        World world = new World(GameMode.BATTLE);
        PlayerShip loser = world.players().get(1);

        for (int i = 0; i < 10 && !loser.isOut(); i++) {
            clearGracePeriod(loser);
            loser.takeDamage(1000);
        }

        assertTrue(loser.isOut());
        SpawnDirector director = new SpawnDirector(
                new Random(1), Difficulty.NORMAL, GameMode.BATTLE.rules());
        RoundResult result = RoundResult.of(world, director);
        assertEquals(1, result.winningPlayerNumber(), "the surviving player should be named");
        assertFalse(world.players().get(0).isOut());
    }

    private static void clearGracePeriod(PlayerShip ship) {
        for (int i = 0; i <= 120; i++) {
            ship.tickTimers();
        }
    }

    private static Bullet bulletOnTopOf(PlayerShip target, PlayerShip owner) {
        Bullet bullet = new Bullet(Sprite.PLAYER_BULLET,
                target.centerX(), target.centerY(), 0, 0, owner, 25);
        return bullet;
    }
}
