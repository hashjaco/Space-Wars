package com.hashimjacobs.spacecase;

/** Fixed arena and gameplay tuning. Values that vary by difficulty live in {@code prefs.Difficulty}. */
public final class GameConfig {

    public static final double WIDTH = 996;
    public static final double HEIGHT = 864;

    public static final int PLAYER_HEALTH = 100;
    public static final int PLAYER_LIVES = 3;
    public static final double PLAYER_SPEED = 5.5;
    public static final double PLAYER_SPEED_BOOSTED = 8.5;
    public static final int PLAYER_FIRE_COOLDOWN = 11;
    public static final int PLAYER_INVULNERABLE_TICKS = 90;

    public static final double BULLET_SPEED = 10.0;
    public static final int BULLET_DAMAGE = 10;
    public static final int MEGA_BULLET_DAMAGE = 34;

    public static final double ENEMY_BULLET_SPEED = 4.5;
    public static final int ENEMY_BULLET_DAMAGE = 6;
    public static final int ENEMY_CONTACT_DAMAGE = 25;

    public static final double POWERUP_DRIFT_SPEED = 1.6;
    public static final int POWERUP_LIFETIME_TICKS = 620;
    /** Ticks a timed power-up (tri-shot, mega laser, speed) stays active once collected. */
    public static final int POWERUP_DURATION_TICKS = 640;

    public static final double BACKGROUND_SCROLL_SPEED = 1.5;

    private GameConfig() {
    }
}
