package com.hashimjacobs.spacecase.asset;

import com.hashimjacobs.spacecase.GameConfig;

/**
 * Every image the game draws, with the on-screen size it is decoded at.
 *
 * Sprites used to be looked up by string out of a HashMap, which silently returned null for the
 * several keys that were never registered; those nulls reached GraphicsContext.drawImage and threw
 * on the render thread. Enum constants make a missing sprite a compile error instead.
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

    ENEMY_SCOUT("invader-animated-red.gif", 40, 34),
    ENEMY_FIGHTER("daShootasBossKill.png", 66, 66),
    ENEMY_CRUISER("enemyShip2.png", 74, 88),
    BOSS("enemyShip3.png", 190, 190),

    ASTEROID_SMALL("asteroid.png", 34, 34),
    ASTEROID_BIG("aNuttaAsteroid.png", 56, 56),
    ASTEROID_HUGE("aNuttaAsteroid.png", 88, 88),

    PLAYER_BULLET("PlayProjectile.png", 16, 22),
    ENEMY_BULLET("EnemyProjectile1.png", 16, 22),
    MEGA_BULLET("MegaLaser.png", 26, 40),
    TRI_BULLET_LEFT("triBulletL.png", 18, 24),
    TRI_BULLET_UP("triBulletU.png", 18, 26),
    TRI_BULLET_RIGHT("triBulletR.png", 18, 24),

    PICKUP_TRI_SHOT("triBulletPU.png", 36, 36),
    PICKUP_MEGA_LASER("MegaLaser.png", 34, 40),
    PICKUP_SHIELD("shield.png", 38, 38),
    PICKUP_HEALTH("healthPU.png", 36, 36),
    PICKUP_SPEED("redbull.png", 26, 40),
    PICKUP_EXTRA_LIFE("extraLife.gif", 34, 34),

    SHIELD_AURA("shield.png", 78, 78),
    BACKGROUND("spaceBackground.gif", GameConfig.WIDTH, GameConfig.HEIGHT);

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
