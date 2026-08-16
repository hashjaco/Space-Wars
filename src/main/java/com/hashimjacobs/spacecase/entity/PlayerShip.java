package com.hashimjacobs.spacecase.entity;

import java.util.EnumMap;
import java.util.Map;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.garage.Upgrade;

/** A player-controlled ship: health, lives, score, and whatever power-ups are currently running. */
public final class PlayerShip extends Entity {

    private final int playerNumber;
    private final String name;
    /**
     * Where this ship starts and which way it points.
     *
     * Not final, because a level can change the geometry of the arena underneath a ship that
     * outlives it: arriving at a side-view level has to move both pilots to the left wall and turn
     * them right. The class already does this kind of live surgery for loadouts.
     */
    private Facing facing;
    private double spawnX;
    private double spawnY;

    private final Map<PowerUp.Kind, Integer> activeEffects = new EnumMap<>(PowerUp.Kind.class);

    private int health = GameConfig.PLAYER_HEALTH;
    private int lives = GameConfig.PLAYER_LIVES;
    private int score;
    private int enemiesKilled;
    private int asteroidsDestroyed;
    private int shotsFired;
    private int shotsHit;
    private int damageTaken;
    private int fireCooldown;
    private int invulnerableTicks;
    private int hitFlashTicks;
    private Lean lean = Lean.NONE;

    /**
     * What the pilot has bought. Stock until the garage or a saved pilot record replaces it, so
     * every existing caller keeps working and an unupgraded ship flies exactly as it always did.
     */
    private Loadout loadout;

    public PlayerShip(int playerNumber, String name, Facing facing, double spawnX, double spawnY) {
        super(playerNumber == 1 ? Sprite.P1_STRAIGHT : Sprite.P2_STRAIGHT, spawnX, spawnY);
        this.playerNumber = playerNumber;
        this.name = name;
        this.facing = facing;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.loadout = Loadout.stock(playerNumber);
    }

    /**
     * Fits a loadout mid-run.
     *
     * The ship object outlives every level, so this is live surgery rather than construction: the
     * garage hands back an upgraded loadout between levels and the next shot is already stronger.
     * Refreshes the sprite because the paint job may have changed with it.
     */
    public void applyLoadout(Loadout loadout) {
        this.loadout = loadout;
        // Health is not topped up here. Buying hull mid-run raises the ceiling; the extra points
        // arrive on the next respawn or health pickup, so an upgrade cannot double as a heal.
        refreshSprite();
    }

    public Loadout loadout() {
        return loadout;
    }

    /** Advances every per-tick timer. Call once per frame before movement. */
    public void tickTimers() {
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (invulnerableTicks > 0) {
            invulnerableTicks--;
        }
        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }
        activeEffects.replaceAll((kind, remaining) -> remaining - 1);
        activeEffects.values().removeIf(remaining -> remaining <= 0);
        refreshSprite();
    }

    public boolean canFire() {
        boolean ready = fireCooldown <= 0;
        return ready;
    }

    /** The single choke point for every fire mode, so the fire-rate upgrade applies to all of them. */
    public void startFireCooldown() {
        int upgraded = GameConfig.PLAYER_FIRE_COOLDOWN - loadout.level(Upgrade.FIRE_RATE);
        fireCooldown = Math.max(GameConfig.PLAYER_FIRE_COOLDOWN_FLOOR, upgraded);
    }

    /**
     * Applies damage unless shielded or still invulnerable from a respawn.
     * Returns true when the hit cost the player a life.
     */
    public boolean takeDamage(int amount) {
        if (invulnerableTicks > 0 || hasEffect(PowerUp.Kind.SHIELD)) {
            return false;
        }
        // Plating reduces a hit, never cancels it: a maxed ship that could not be scratched by the
        // weakest scout would make whole waves free. The shield power-up above is the only thing
        // that stops damage outright, and it is temporary.
        int taken = Math.max(1, amount - loadout.level(Upgrade.SHIELDING) * GameConfig.UPGRADE_SHIELD_STEP);
        health -= taken;
        damageTaken += taken;
        hitFlashTicks = 18;
        if (health > 0) {
            return false;
        }
        lives--;
        respawn();
        return true;
    }

    private void respawn() {
        health = maxHealth();
        setPosition(spawnX, spawnY);
        setVelocity(0, 0);
        invulnerableTicks = GameConfig.PLAYER_INVULNERABLE_TICKS;
        activeEffects.clear();
    }

    public void collect(PowerUp.Kind kind) {
        switch (kind) {
            case HEALTH -> health = maxHealth();
            case EXTRA_LIFE -> lives++;
            default -> activeEffects.put(kind, GameConfig.POWERUP_DURATION_TICKS);
        }
    }

    public boolean hasEffect(PowerUp.Kind kind) {
        boolean active = activeEffects.containsKey(kind);
        return active;
    }

    public int remainingEffectTicks(PowerUp.Kind kind) {
        int remaining = activeEffects.getOrDefault(kind, 0);
        return remaining;
    }

    /**
     * Movement speed, thrusters included.
     *
     * The upgrade is added to the base rather than to the boosted figure, and is tuned so a maxed
     * ship is still slower than the speed pickup makes it -- otherwise collecting one would be a
     * downgrade. {@code garage.UpgradeBalanceTest} holds that line.
     */
    public double speed() {
        double base = hasEffect(PowerUp.Kind.SPEED)
                ? GameConfig.PLAYER_SPEED_BOOSTED
                : GameConfig.PLAYER_SPEED;
        double speed = base + loadout.level(Upgrade.SPEED) * GameConfig.UPGRADE_SPEED_STEP;
        return speed;
    }

    /** A shot's damage with firepower applied. Every fire mode routes through here. */
    public int damageFor(int baseDamage) {
        int bonus = (int) Math.round(baseDamage * loadout.level(Upgrade.FIREPOWER)
                * GameConfig.UPGRADE_DAMAGE_STEP);
        return baseDamage + bonus;
    }

    /** The body-kit decal for the current pose, or null when flying stock. */
    public Sprite kitOverlay() {
        Sprite decal = loadout.kit().overlay(lean.ordinal());
        return decal;
    }

    /** Sets which way the ship is leaning so the renderer can pick the banked sprite. */
    public void setLean(Lean lean) {
        this.lean = lean;
        Sprite next = loadout.livery().pose(lean.ordinal(), hitFlashTicks > 0);
        setSprite(next);
    }

    /** Refreshes the sprite after a hit flash starts or ends, without changing the lean. */
    public void refreshSprite() {
        setLean(lean);
    }

    /** Moves this ship's start point for a level that runs a different way. Between levels only. */
    public void setSpawn(double x, double y, Facing facing) {
        this.spawnX = x;
        this.spawnY = y;
        this.facing = facing;
    }

    /** Puts the ship back where it started between levels, keeping health, lives and score. */
    public void returnToSpawn() {
        setPosition(spawnX, spawnY);
        setVelocity(0, 0);
        setLean(Lean.NONE);
    }

    /**
     * Back into the fight with a fresh set of lives, when a partner cleared the level alone.
     *
     * Reuses {@link #respawn()} rather than repeating it, so a revived ship gets the same full
     * health, spawn position and grace period a normal death does.
     */
    public void revive() {
        lives = GameConfig.PLAYER_LIVES;
        respawn();
    }

    public boolean isOut() {
        boolean out = lives <= 0;
        return out;
    }

    public boolean isInvulnerable() {
        boolean invulnerable = invulnerableTicks > 0;
        return invulnerable;
    }

    /**
     * True for a short window after damage actually landed, for the impact flash.
     *
     * A predicate rather than a getter for the tick count: every caller wants the boolean, and none
     * of them should have to know how long the window is. Note it is only set when a hit got
     * through -- a shot absorbed by a shield or the respawn grace period does not flash.
     */
    public boolean justHit() {
        boolean flashing = hitFlashTicks > 0;
        return flashing;
    }

    public int playerNumber() {
        return playerNumber;
    }

    /** The pilot flying it, drawn under the hull and credited with the career score. */
    public String name() {
        return name;
    }

    public Facing facing() {
        return facing;
    }

    public int health() {
        return health;
    }

    /**
     * Full health for this ship.
     *
     * Everything that fills, refills or measures the health bar goes through here rather than
     * reading {@code GameConfig.PLAYER_HEALTH} directly, so a hull upgrade cannot leave one of them
     * behind -- the HUD in particular used to divide by the constant and would have drawn an
     * over-full bar.
     */
    public int maxHealth() {
        int upgraded = GameConfig.PLAYER_HEALTH
                + loadout.level(Upgrade.HULL) * GameConfig.UPGRADE_HULL_STEP;
        return upgraded;
    }

    public int lives() {
        return lives;
    }

    public int score() {
        return score;
    }

    public void addScore(int amount) {
        score += amount;
    }

    public int enemiesKilled() {
        return enemiesKilled;
    }

    public void recordEnemyKill() {
        enemiesKilled++;
    }

    public int asteroidsDestroyed() {
        return asteroidsDestroyed;
    }

    public void recordAsteroidKill() {
        asteroidsDestroyed++;
    }

    public int shotsFired() {
        return shotsFired;
    }

    /** Counts one trigger pull, not one projectile: a tri-shot volley is a single shot to hit with. */
    public void recordShot() {
        shotsFired++;
    }

    public int shotsHit() {
        return shotsHit;
    }

    public void recordHit() {
        shotsHit++;
    }

    /** Damage absorbed across the run, which the debrief pays an untouched bonus against. */
    public int damageTaken() {
        return damageTaken;
    }

    /**
     * Everything about a ship worth carrying across a save.
     *
     * Only the counters: not the loadout, which a saved run picks up fresh from the pilot's record
     * so a replay flies the ship you own now rather than the one you owned then, and not the
     * per-tick timers, which are all zero at the level boundary a checkpoint is taken at.
     */
    public record Progress(int health, int lives, int score, int enemiesKilled,
                           int asteroidsDestroyed, int shotsFired, int shotsHit, int damageTaken) {
    }

    public Progress progress() {
        return new Progress(health, lives, score, enemiesKilled, asteroidsDestroyed,
                shotsFired, shotsHit, damageTaken);
    }

    /**
     * Puts a saved run's counters back on a fresh ship. Only ever called before the first tick.
     *
     * Health is clamped to this ship's ceiling rather than trusted -- the save may predate a hull
     * upgrade, or postdate one since reloaded onto a different pilot -- and lives are floored at
     * one, because a checkpoint of a ship with none is a record of a round that had already ended.
     */
    public void restore(Progress saved) {
        health = Math.max(1, Math.min(saved.health(), maxHealth()));
        lives = Math.max(1, saved.lives());
        score = Math.max(0, saved.score());
        enemiesKilled = Math.max(0, saved.enemiesKilled());
        asteroidsDestroyed = Math.max(0, saved.asteroidsDestroyed());
        shotsFired = Math.max(0, saved.shotsFired());
        shotsHit = Math.max(0, saved.shotsHit());
        damageTaken = Math.max(0, saved.damageTaken());
    }

    /**
     * Horizontal banking, which selects the ship's sprite.
     *
     * Declaration order matters: {@link com.hashimjacobs.spacecase.garage.Livery} and
     * {@link com.hashimjacobs.spacecase.garage.Kit} index their frame lists by ordinal, so these
     * run from hardest left to hardest right.
     */
    public enum Lean {
        HARD_LEFT, LEFT, NONE, RIGHT, HARD_RIGHT
    }
}
