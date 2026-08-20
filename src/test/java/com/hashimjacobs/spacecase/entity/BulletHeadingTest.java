package com.hashimjacobs.spacecase.entity;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A round's art has to point where the round is going, in either orientation.
 *
 * Zero is straight up because that is how the projectile sprites are cut, and the angle grows
 * clockwise because that is the way the canvas turns.
 */
class BulletHeadingTest {

    private static double heading(double velocityX, double velocityY) {
        return new Bullet(Sprite.PLAYER_BULLET, 0, 0, velocityX, velocityY, null, 1)
                .headingDegrees();
    }

    @Test
    void aTopDownRoundPointsUpTheArenaAndAnEnemyRoundPointsBackDownIt() {
        assertEquals(0, heading(0, -8), 1e-9);
        assertEquals(180, heading(0, 8), 1e-9);
    }

    @Test
    void aSideViewRoundPointsAcrossTheScreenInsteadOfUpIt() {
        assertEquals(90, heading(8, 0), 1e-9);
        assertEquals(-90, heading(-8, 0), 1e-9);
    }

    /** The tri-shot and the homing rocket are why this reads velocity rather than a Facing. */
    @Test
    void anOffAxisRoundGetsAnOffAxisAngle() {
        assertEquals(45, heading(8, -8), 1e-9);
    }
}
