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

    /**
     * The garage tracks that improve a pickup rather than the ship.
     *
     * These compound with something the player already had to find, so each is deliberately a short
     * ladder with a high price: two levels, not four. The floors matter more than the steps -- a
     * reload that keeps shrinking eventually turns the rocket into the primary weapon, which is the
     * one thing the pickup split exists to prevent.
     */
    public static final int UPGRADE_SALVO_STEP = 5;
    public static final int ROCKET_FIRE_COOLDOWN_FLOOR = 14;
    public static final int UPGRADE_BEAM_STEP = 1;

    /** Ticks off the scythe's reload per level of the hone. */
    public static final int UPGRADE_HONE_STEP = 5;

    /** Extra pellets per level of the choke. */
    public static final int UPGRADE_CHOKE_STEP = 2;

    /** Pixels of blast radius per level of the yield. */
    public static final int UPGRADE_YIELD_STEP = 30;
    public static final int UPGRADE_CAPACITOR_STEP = 15;

    /**
     * The repair rig: one point of health per interval, and only while flying clean.
     *
     * Divided by the level, so level three patches three times as often as level one. Gated on not
     * having been hit recently, and slow enough that it cannot outheal being shot -- an enemy bullet
     * is six points, so even maxed this is worth about a fifth of one shot a second. It is for the
     * long quiet stretch between waves, not for standing in fire.
     */
    public static final int REPAIR_INTERVAL_TICKS = 900;
    public static final int REPAIR_CALM_TICKS = 180;

    /** Extra respawn grace per level of eject gear, in ticks. */
    public static final int UPGRADE_EJECT_STEP = 30;

    /**
     * How far a collector reaches per level, and how fast it reels a pickup in.
     *
     * The pull is well under {@code PLAYER_SPEED} on purpose: it should save a pilot the detour for
     * something already nearly in reach, not fetch the arena to them.
     */
    public static final int UPGRADE_COLLECTOR_RANGE = 90;
    public static final double UPGRADE_COLLECTOR_PULL = 2.2;

    public static final double BULLET_SPEED = 10.0;
    public static final int BULLET_DAMAGE = 10;

    /**
     * The mega laser burns rather than hits: this lands every tick the beam is held on a target.
     *
     * Compare an ordinary shot, which is ten points every six ticks. Three a tick is nearly twice
     * that against one target, and the beam pierces, so a packed lane takes it all at once. That is
     * the whole appeal of the pickup and the reason it has no ammunition.
     */
    public static final int BEAM_DAMAGE_PER_TICK = 3;

    /** How wide the beam is across the lane, for both the damage box and the drawn core. */
    public static final double BEAM_WIDTH = 20;

    /**
     * The player's rockets: the old mega shot's art, now steering.
     *
     * Turns harder than the flagship's because it chases small, fast enemies rather than a player
     * trying to escape, and the fuse is short so a missed pair does not orbit into the next wave.
     */
    public static final double PLAYER_ROCKET_SPEED = 6.0;
    public static final int PLAYER_ROCKET_DAMAGE = 45;
    public static final double PLAYER_ROCKET_TURN_RATE = 0.05;
    public static final int PLAYER_ROCKET_FUSE_TICKS = 180;

    /** Rockets fire far slower than the gun, which is what keeps 45 points a shot fair. */
    public static final int ROCKET_FIRE_COOLDOWN = 26;

    /**
     * The scythe: a wide blade that crosses the lane and does not stop at the first hull.
     *
     * Slower than a bullet and reloaded far slower than the gun, because piercing a group of eight
     * is worth about eight shots. Its damage is per ship burned, not per throw.
     */
    public static final double SCYTHE_SPEED = 5.0;
    public static final int SCYTHE_DAMAGE = 14;
    public static final int SCYTHE_FIRE_COOLDOWN = 30;

    /**
     * The flak: a cone of short-lived pellets, devastating close and useless past its fuse.
     *
     * The fuse is the range. At {@code FLAK_SPEED} a pellet covers about two hundred pixels before
     * it expires, which is a quarter of the arena -- close enough that using this weapon means
     * closing with the thing you are shooting.
     */
    public static final double FLAK_SPEED = 13.0;
    public static final int FLAK_DAMAGE = 9;
    public static final int FLAK_PELLETS = 7;
    public static final double FLAK_SPREAD = 2.6;
    public static final int FLAK_FUSE_TICKS = 16;
    public static final int FLAK_FIRE_COOLDOWN = 20;

    /**
     * The nova: a slow shell that detonates on what it touches.
     *
     * The radius is what it is for, so the direct hit is deliberately weak -- a nova aimed at one
     * scout is a wasted shell, and a nova into a flagship's escort is the whole point.
     */
    public static final double NOVA_SPEED = 3.6;
    public static final int NOVA_DAMAGE = 20;
    public static final int NOVA_BLAST_DAMAGE = 55;
    public static final double NOVA_BLAST_RADIUS = 130;
    public static final int NOVA_FIRE_COOLDOWN = 46;

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

    /**
     * Damage the shield pickup soaks before it fails. Power-ups themselves never expire; dying is
     * what costs you them, so this is the only thing a shield is spent by.
     */
    public static final int SHIELD_CAPACITY = 60;

    /** Tri-shot stacks: one pickup gives three streams, each further stack adds one, to five. */
    public static final int TRI_SHOT_MAX_STACKS = 3;

    public static final double BACKGROUND_SCROLL_SPEED = 1.5;

    private GameConfig() {
    }
}
