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

    /**
     * The garage economy, all of it.
     *
     * Four numbers decide how long a pilot grinds: what a step costs, and how much each step gives.
     * One upgrade track costs 40+80+120+160 = 400 credits to max and all five cost 2000, against
     * roughly 700-900 earned in a full eight-level run -- so about three runs to max everything,
     * with paint jobs and body kits competing for the same credits. Change these together.
     */
    public static final int UPGRADE_MAX_LEVEL = 4;
    public static final int UPGRADE_COST_STEP = 40;
    public static final double UPGRADE_DAMAGE_STEP = 0.25;

    /**
     * Added to base speed per level of thrusters.
     *
     * Must keep {@code PLAYER_SPEED + UPGRADE_MAX_LEVEL * UPGRADE_SPEED_STEP} below
     * {@code PLAYER_SPEED_BOOSTED}, or collecting the speed pickup would slow a maxed ship down.
     * {@code garage.UpgradeBalanceTest} asserts it.
     */
    public static final double UPGRADE_SPEED_STEP = 0.35;

    /** Damage subtracted per level of plating. Never reduces a hit below one point. */
    public static final int UPGRADE_SHIELD_STEP = 1;

    /** Extra maximum health per level of hull. */
    public static final int UPGRADE_HULL_STEP = 15;

    /** Fastest the fire-rate upgrade may make the weapon, whatever the level. */
    public static final int PLAYER_FIRE_COOLDOWN_FLOOR = 6;

    public static final double BULLET_SPEED = 10.0;
    public static final int BULLET_DAMAGE = 10;
    public static final int MEGA_BULLET_DAMAGE = 34;

    public static final double ENEMY_BULLET_SPEED = 4.5;
    public static final int ENEMY_BULLET_DAMAGE = 6;
    public static final int ENEMY_CONTACT_DAMAGE = 25;

    /**
     * The flagship's rocket salvo: slower than its own gunfire, and it hurts.
     *
     * The turn rate is the balance point. At this speed it works out to roughly a 69-pixel turning
     * circle, so a rocket tracks a drifting player but cannot hold a corner against one flying flat
     * out at PLAYER_SPEED. Raise it and the salvo stops being dodgeable.
     */
    public static final double BOSS_ROCKET_SPEED = 2.4;
    public static final int BOSS_ROCKET_DAMAGE = 20;
    public static final double BOSS_ROCKET_TURN_RATE = 0.035;

    /**
     * A hydra head's acid: slower than a rocket, turns harder, hurts less.
     *
     * Tuned to make you keep moving rather than to kill outright. It out-turns a rocket, so you
     * cannot simply hold a corner, but at this speed a ship at full throttle always outruns it --
     * three heads spitting means three of these in the air, and they have to be survivable
     * together.
     */
    public static final double ACID_SPEED = 1.9;
    public static final int ACID_DAMAGE = 12;
    public static final double ACID_TURN_RATE = 0.055;

    public static final double POWERUP_DRIFT_SPEED = 1.6;
    public static final int POWERUP_LIFETIME_TICKS = 620;
    /** Ticks a timed power-up (tri-shot, mega laser, speed) stays active once collected. */
    public static final int POWERUP_DURATION_TICKS = 640;

    public static final double BACKGROUND_SCROLL_SPEED = 1.5;

    private GameConfig() {
    }
}
