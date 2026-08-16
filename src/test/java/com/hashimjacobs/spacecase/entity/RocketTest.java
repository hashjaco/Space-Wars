package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocketTest {

    private static PlayerShip pilotAt(double x, double y) {
        return new PlayerShip(1, "TARGET", Facing.UP, x, y);
    }

    private static Rocket firedDownAt(PlayerShip target) {
        return new Rocket(500, 100, target, GameConfig.BOSS_ROCKET_SPEED,
                GameConfig.BOSS_ROCKET_DAMAGE, GameConfig.BOSS_ROCKET_TURN_RATE);
    }

    private static double speedOf(Rocket rocket) {
        return Math.hypot(rocket.velocityX(), rocket.velocityY());
    }

    @Test
    void itTurnsTowardATargetOffToOneSide() {
        PlayerShip target = pilotAt(100, 700);
        Rocket rocket = firedDownAt(target);
        assertEquals(0, rocket.velocityX(), 1e-9, "it launches straight down");

        for (int i = 0; i < 40; i++) {
            rocket.update();
        }

        assertTrue(rocket.velocityX() < 0, "should have swung left toward the target");
    }

    /** Steering renormalises rather than adding to the velocity, or a rocket would accelerate. */
    @Test
    void turningDoesNotChangeItsSpeed() {
        Rocket rocket = firedDownAt(pilotAt(100, 700));

        for (int i = 0; i < 120; i++) {
            rocket.update();
            assertEquals(GameConfig.BOSS_ROCKET_SPEED, speedOf(rocket), 1e-6);
        }
    }

    @Test
    void itTurnsNoFasterThanItsTurnRate() {
        Rocket rocket = firedDownAt(pilotAt(100, 700));
        double previous = Math.atan2(rocket.velocityY(), rocket.velocityX());

        for (int i = 0; i < 30; i++) {
            rocket.update();
            double heading = Math.atan2(rocket.velocityY(), rocket.velocityX());
            double turned = Math.abs(Math.atan2(Math.sin(heading - previous),
                    Math.cos(heading - previous)));
            assertTrue(turned <= GameConfig.BOSS_ROCKET_TURN_RATE + 1e-9,
                    "turned " + turned + " in one tick");
            previous = heading;
        }
    }

    /**
     * The fuse is what stops a missed salvo orbiting forever: the world only culls bullets that
     * leave the top or bottom of the arena, and a rocket circling its target never does.
     */
    @Test
    void itBurnsOutEventually() {
        Rocket rocket = firedDownAt(pilotAt(500, 400));

        for (int i = 0; i < 419; i++) {
            rocket.update();
        }
        assertTrue(rocket.isAlive(), "should still be flying just before the fuse expires");

        rocket.update();

        assertFalse(rocket.isAlive());
    }

    @Test
    void aTargetWithNoLivesLeftIsNoLongerChased() {
        PlayerShip target = pilotAt(100, 700);
        for (int i = 0; i < GameConfig.PLAYER_LIVES; i++) {
            target.takeDamage(GameConfig.PLAYER_HEALTH);
            for (int t = 0; t <= GameConfig.PLAYER_INVULNERABLE_TICKS; t++) {
                target.tickTimers();
            }
        }
        assertTrue(target.isOut());
        Rocket rocket = firedDownAt(target);

        for (int i = 0; i < 40; i++) {
            rocket.update();
        }

        assertEquals(0, rocket.velocityX(), 1e-9, "it should fly straight on rather than steer");
    }

    @Test
    void aRocketWithNoTargetFliesStraight() {
        Rocket rocket = new Rocket(500, 100, null, GameConfig.BOSS_ROCKET_SPEED,
                GameConfig.BOSS_ROCKET_DAMAGE, GameConfig.BOSS_ROCKET_TURN_RATE);

        for (int i = 0; i < 40; i++) {
            rocket.update();
        }

        assertEquals(0, rocket.velocityX(), 1e-9);
        assertTrue(rocket.y() > 100);
    }

    @Test
    void itCountsAsEnemyFire() {
        Rocket rocket = firedDownAt(pilotAt(100, 700));

        assertFalse(rocket.firedByPlayer(), "a boss rocket must not be able to hurt the boss");
        assertEquals(GameConfig.BOSS_ROCKET_DAMAGE, rocket.damage());
    }
}
