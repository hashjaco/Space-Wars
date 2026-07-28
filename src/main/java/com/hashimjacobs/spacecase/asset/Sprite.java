package com.hashimjacobs.spacecase.asset;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * Every image the game draws, with the on-screen size it is decoded at.
 *
 * Sprites used to be looked up by string out of a HashMap, which silently returned null for the
 * several keys that were never registered; those nulls reached GraphicsContext.drawImage and threw
 * on the render thread. Enum constants make a missing sprite a compile error instead.
 *
 * Provenance for every file is recorded in ASSETS.md.
 */
public enum Sprite {

    P1_STRAIGHT("daShootaStraight.png", 56, 74),
    P1_LEFT("shootaLeft1.png", 56, 60),
    P1_RIGHT("daShootasRight.png", 56, 60),
    P1_STRAIGHT_HIT("daShootaStraightDamage.png", 56, 74),
    P1_LEFT_HIT("shootaLeftDamage.png", 56, 60),
    P1_RIGHT_HIT("daShootasRightDamage.png", 56, 60),

    P2_STRAIGHT("daShootaStraight2.png", 56, 74),
    P2_LEFT("shootaLeft2.png", 56, 60),
    P2_RIGHT("daShootasRight2.png", 56, 60),
    // The art has no player-2-specific damage frames; the shared ones read as scorch marks.
    P2_STRAIGHT_HIT("daShootaStraightDamage.png", 56, 74),
    P2_LEFT_HIT("shootaLeftDamage.png", 56, 60),
    P2_RIGHT_HIT("daShootasRightDamage.png", 56, 60),

    ENEMY_SCOUT("enemy-scout.png", 46, 41),
    ENEMY_FIGHTER("enemy-fighter.png", 64, 60),
    ENEMY_CRUISER("enemy-cruiser.png", 74, 83),
    BOSS("boss.png", 210, 162),

    ASTEROID_SMALL("asteroid-small.png", 36, 36),
    ASTEROID_BIG("asteroid-big.png", 58, 58),
    ASTEROID_HUGE("asteroid-huge.png", 92, 92),

    PLAYER_BULLET("PlayProjectile.png", 16, 22),
    ENEMY_BULLET("EnemyProjectile1.png", 16, 22),
    MEGA_BULLET("MegaLaser.png", 26, 40),
    TRI_BULLET_LEFT("triBulletL.png", 18, 24),
    TRI_BULLET_UP("triBulletU.png", 18, 26),
    TRI_BULLET_RIGHT("triBulletR.png", 18, 24),

    PICKUP_TRI_SHOT("pickup-tri-shot.png", 36, 36),
    PICKUP_MEGA_LASER("pickup-mega-laser.png", 36, 36),
    PICKUP_SHIELD("pickup-shield.png", 36, 36),
    PICKUP_HEALTH("pickup-health.png", 36, 36),
    PICKUP_SPEED("pickup-speed.png", 36, 36),
    PICKUP_EXTRA_LIFE("pickup-extra-life.png", 36, 36),

    SHIELD_AURA("pickup-shield.png", 84, 84),

    /** Three starfield layers, scrolled at different rates for parallax depth. */
    BACKGROUND_FAR("background-far.png", GameConfig.WIDTH, GameConfig.HEIGHT),
    BACKGROUND_MID("background-mid.png", GameConfig.WIDTH, GameConfig.HEIGHT),
    BACKGROUND_NEAR("background-near.png", GameConfig.WIDTH, GameConfig.HEIGHT);

    private final String fileName;
    private final double width;
    private final double height;

    Sprite(String fileName, double width, double height) {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
    }

    public String resourcePath() {
        String path = "/sprites/" + fileName;
        return path;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }
}
