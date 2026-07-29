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

    /**
     * Player hulls: five bank poses each, plus a damage frame per pose.
     *
     * All twenty are cut from one spritesheet and padded to a common canvas, so every pose decodes to
     * the same size and the ship does not appear to grow as it banks.
     */
    P1_BANK_LEFT("player/p1-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_LEFT("player/p1-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_STRAIGHT("player/p1-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_RIGHT("player/p1-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_RIGHT("player/p1-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_LEFT_HIT("player/p1-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_LEFT_HIT("player/p1-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_STRAIGHT_HIT("player/p1-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_RIGHT_HIT("player/p1-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P1_BANK_RIGHT_HIT("player/p1-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    P2_BANK_LEFT("player/p2-bank-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_LEFT("player/p2-left.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_STRAIGHT("player/p2-straight.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_RIGHT("player/p2-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_RIGHT("player/p2-bank-right.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_LEFT_HIT("player/p2-bank-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_LEFT_HIT("player/p2-left-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_STRAIGHT_HIT("player/p2-straight-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_RIGHT_HIT("player/p2-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),
    P2_BANK_RIGHT_HIT("player/p2-bank-right-hit.png", Draw.PLAYER_W, Draw.PLAYER_H),

    /**
     * Enemy hulls, one faction per level.
     *
     * The three archetypes keep their sizes across all eight levels, so a scout is the same target
     * wherever it is met; only the palette and the build change. Which set spawns comes from
     * {@code mode.Level}.
     */
    L1_SCOUT("level-1/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L1_FIGHTER("level-1/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L1_CRUISER("level-1/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L2_SCOUT("level-2/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L2_FIGHTER("level-2/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L2_CRUISER("level-2/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L3_SCOUT("level-3/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L3_FIGHTER("level-3/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L3_CRUISER("level-3/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L4_SCOUT("level-4/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L4_FIGHTER("level-4/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L4_CRUISER("level-4/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L5_SCOUT("level-5/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L5_FIGHTER("level-5/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L5_CRUISER("level-5/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L6_SCOUT("level-6/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L6_FIGHTER("level-6/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L6_CRUISER("level-6/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L7_SCOUT("level-7/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L7_FIGHTER("level-7/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L7_CRUISER("level-7/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    L8_SCOUT("level-8/enemy-scout.png", Draw.SCOUT_W, Draw.SCOUT_H),
    L8_FIGHTER("level-8/enemy-fighter.png", Draw.FIGHTER_W, Draw.FIGHTER_H),
    L8_CRUISER("level-8/enemy-cruiser.png", Draw.CRUISER_W, Draw.CRUISER_H),

    /**
     * Fallback only. Bosses are animated, so the renderer draws a frame from
     * {@link BossArt} instead of this -- but {@code Entity} requires some sprite, and a visible ship
     * beats a null image if a new draw path ever misses the animation branch.
     */
    BOSS("boss-sentinel/1.png", 210, 162),

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

    /**
     * Three backdrop layers per level, scrolled at different rates for parallax depth.
     *
     * Which set is drawn comes from {@code mode.Level}; the renderer never names these directly. Each
     * one is a full arena, so they are the only sprites decoded on demand rather than at startup --
     * three are on screen at a time and twenty-four exist.
     */
    L1_FAR("level-1/far.png"),
    L1_MID("level-1/mid.png"),
    L1_NEAR("level-1/near.png"),

    L2_FAR("level-2/far.png"),
    L2_MID("level-2/mid.png"),
    L2_NEAR("level-2/near.png"),

    L3_FAR("level-3/far.png"),
    L3_MID("level-3/mid.png"),
    L3_NEAR("level-3/near.png"),

    L4_FAR("level-4/far.png"),
    L4_MID("level-4/mid.png"),
    L4_NEAR("level-4/near.png"),

    L5_FAR("level-5/far.png"),
    L5_MID("level-5/mid.png"),
    L5_NEAR("level-5/near.png"),

    L6_FAR("level-6/far.png"),
    L6_MID("level-6/mid.png"),
    L6_NEAR("level-6/near.png"),

    L7_FAR("level-7/far.png"),
    L7_MID("level-7/mid.png"),
    L7_NEAR("level-7/near.png"),

    L8_FAR("level-8/far.png"),
    L8_MID("level-8/mid.png"),
    L8_NEAR("level-8/near.png");

    /**
     * Draw sizes shared by a whole family of sprites, so a hull is the same target in every level.
     *
     * A nested holder rather than fields on the enum itself: a constant cannot forward-reference a
     * static of its own class, and the sizes have to be declared before the constants that use them.
     */
    private static final class Draw {
        static final double PLAYER_W = 60;
        static final double PLAYER_H = 64;
        static final double SCOUT_W = 46;
        static final double SCOUT_H = 41;
        static final double FIGHTER_W = 64;
        static final double FIGHTER_H = 60;
        static final double CRUISER_W = 74;
        static final double CRUISER_H = 83;

        private Draw() {
        }
    }

    private final String fileName;
    private final double width;
    private final double height;
    private final boolean decodedOnDemand;

    Sprite(String fileName, double width, double height) {
        this(fileName, width, height, false);
    }

    /** An arena-sized backdrop layer, faulted in on first use. */
    Sprite(String fileName) {
        this(fileName, GameConfig.WIDTH, GameConfig.HEIGHT, true);
    }

    Sprite(String fileName, double width, double height, boolean decodedOnDemand) {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.decodedOnDemand = decodedOnDemand;
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

    /** Whether {@code Assets.load()} skips this one and leaves it to be decoded on first request. */
    public boolean decodedOnDemand() {
        return decodedOnDemand;
    }
}
