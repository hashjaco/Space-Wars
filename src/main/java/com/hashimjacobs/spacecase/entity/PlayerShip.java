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

    /**
     * What the pilot is carrying, and how much of it.
     *
     * The value is no longer a countdown. It is a stack count for the tri-shot, the damage the
     * shield can still soak, and a plain one for everything else -- so the map answers both "is
     * this running" and "how much is left" without a second field per effect.
     */
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

    /** Ticks since anything last landed, and progress toward the next repaired point. */
    private int calmTicks;
    private int repairTicks;
    private int hitFlashTicks;
    private boolean firingBeam;
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
        tickRepair();
        refreshSprite();
    }

    /**
     * The repair rig, ticking.
     *
     * Two clocks rather than one: {@link #calmTicks} counts how long since anything landed, and the
     * repair only runs once that passes {@code REPAIR_CALM_TICKS}. Without the calm gate this would
     * heal through a firefight, which is the difference between a rig that rewards clean flying and
     * one that removes the consequence of being shot.
     */
    private void tickRepair() {
        int level = loadout.level(Upgrade.REPAIR);
        if (level <= 0 || health >= maxHealth()) {
            calmTicks++;
            return;
        }
        calmTicks++;
        if (calmTicks < GameConfig.REPAIR_CALM_TICKS) {
            return;
        }
        repairTicks += level;
        if (repairTicks >= GameConfig.REPAIR_INTERVAL_TICKS) {
            repairTicks = 0;
            health = Math.min(maxHealth(), health + 1);
        }
    }

    public boolean canFire() {
        boolean ready = fireCooldown <= 0;
        return ready;
    }

    /** The single choke point for every fire mode, so the fire-rate upgrade applies to all of them. */
    public void startFireCooldown() {
        startFireCooldown(GameConfig.PLAYER_FIRE_COOLDOWN);
    }

    /**
     * @param base the weapon's own interval before upgrades, for a weapon that does not fire at the
     *             gun's rate -- rockets are far heavier per shot and would be absurd at it
     */
    public void startFireCooldown(int base) {
        int upgraded = base - loadout.level(Upgrade.FIRE_RATE);
        fireCooldown = Math.max(GameConfig.PLAYER_FIRE_COOLDOWN_FLOOR, upgraded);
    }

    /** True while the trigger is held with the mega laser fitted. Set every tick by the controller. */
    public boolean isFiringBeam() {
        return firingBeam;
    }

    public void setFiringBeam(boolean firingBeam) {
        this.firingBeam = firingBeam;
    }

    /**
     * Applies damage unless the shield soaks it or the respawn grace period is still running.
     * Returns true when the hit cost the player a life.
     */
    public boolean takeDamage(int amount) {
        if (invulnerableTicks > 0) {
            return false;
        }
        if (hasEffect(PowerUp.Kind.SHIELD)) {
            // ponytail: the hit that empties the shield is absorbed whole rather than spilling the
            // remainder into the hull. Simpler, and it means a shield always buys you one more hit
            // than its bar strictly covers, which is the forgiving way round.
            int left = activeEffects.get(PowerUp.Kind.SHIELD) - amount;
            if (left > 0) {
                activeEffects.put(PowerUp.Kind.SHIELD, left);
            } else {
                activeEffects.remove(PowerUp.Kind.SHIELD);
            }
            return false;
        }
        // Plating reduces a hit, never cancels it: a maxed ship that could not be scratched by the
        // weakest scout would make whole waves free. The shield power-up above is the only thing
        // that stops damage outright, and it is temporary.
        int taken = Math.max(1, amount - loadout.level(Upgrade.SHIELDING) * GameConfig.UPGRADE_SHIELD_STEP);
        health -= taken;
        damageTaken += taken;
        hitFlashTicks = 18;
        calmTicks = 0;
        repairTicks = 0;
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
        invulnerableTicks = GameConfig.PLAYER_INVULNERABLE_TICKS
                + loadout.level(Upgrade.EJECT) * GameConfig.UPGRADE_EJECT_STEP;
        // Dying costs the whole arsenal. Power-ups do not expire on their own any more, so this is
        // the only thing that takes them away and the only reason to fear a hit once shielded.
        activeEffects.clear();
        firingBeam = false;
    }

    /**
     * Arms a pickup. Exhaustive over {@link PowerUp.Kind} on purpose: a new pickup that nobody
     * decided the rules for should not quietly become a one-stack toggle.
     */
    public void collect(PowerUp.Kind kind) {
        switch (kind) {
            case HEALTH -> health = maxHealth();
            case EXTRA_LIFE -> lives++;
            case TRI_SHOT -> activeEffects.merge(PowerUp.Kind.TRI_SHOT, 1,
                    (held, one) -> Math.min(held + one, GameConfig.TRI_SHOT_MAX_STACKS));
            // A second shield refills rather than adding: a stacked one would be an eventual
            // invulnerability, which is exactly what giving it a bar was meant to end.
            case SHIELD -> activeEffects.put(PowerUp.Kind.SHIELD, shieldCapacity());
            case MEGA_LASER, ROCKETS, SPEED -> activeEffects.put(kind, 1);
        }
    }

    public boolean hasEffect(PowerUp.Kind kind) {
        boolean active = activeEffects.containsKey(kind);
        return active;
    }

    /**
     * The mega laser's footprint: {@code {x, y, width, height}} from the nose to the arena wall.
     *
     * Lives on the ship rather than in the collision or render code because both of them need it
     * and they must agree exactly -- a beam that burns a lane it is not drawn in is the one bug
     * this weapon can have. The box runs along whichever axis the nose points and is
     * {@code GameConfig.BEAM_WIDTH} across, so it turns with the ship in a side-view level for
     * free.
     */
    public double[] beamBox() {
        double half = GameConfig.BEAM_WIDTH / 2;
        double nose = facing.xDirection() != 0
                ? centerX() + facing.xDirection() * width() / 2
                : centerY() + facing.yDirection() * height() / 2;
        return switch (facing) {
            case UP -> new double[] {centerX() - half, 0, GameConfig.BEAM_WIDTH, Math.max(0, nose)};
            case DOWN -> new double[] {centerX() - half, nose, GameConfig.BEAM_WIDTH,
                    Math.max(0, GameConfig.HEIGHT - nose)};
            case LEFT -> new double[] {0, centerY() - half, Math.max(0, nose), GameConfig.BEAM_WIDTH};
            case RIGHT -> new double[] {nose, centerY() - half,
                    Math.max(0, GameConfig.WIDTH - nose), GameConfig.BEAM_WIDTH};
        };
    }

    /** How many tri-shot pickups are stacked, 0 to {@code GameConfig.TRI_SHOT_MAX_STACKS}. */
    public int triStacks() {
        int stacks = activeEffects.getOrDefault(PowerUp.Kind.TRI_SHOT, 0);
        return stacks;
    }

    /** What a shield pickup is worth to this ship, capacitor included. */
    public int shieldCapacity() {
        return GameConfig.SHIELD_CAPACITY
                + loadout.level(Upgrade.CAPACITOR) * GameConfig.UPGRADE_CAPACITOR_STEP;
    }

    /** Damage the shield can still deflect, 0 when there is no shield. */
    public int shieldRemaining() {
        int remaining = activeEffects.getOrDefault(PowerUp.Kind.SHIELD, 0);
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
        Sprite decal = loadout.kit().overlay(lean.ordinal(), facing.horizontal());
        return decal;
    }

    /** Sets which way the ship is leaning so the renderer can pick the banked sprite. */
    public void setLean(Lean lean) {
        this.lean = lean;
        Sprite next = loadout.livery().pose(lean.ordinal(), hitFlashTicks > 0, facing.horizontal());
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
        // The hull is cut differently for a level flown side-on, so the frame has to be re-picked
        // rather than left over from the level before.
        refreshSprite();
    }

    /**
     * Puts the ship back where it started between levels, keeping health, lives and score.
     *
     * Power-ups do not survive it. They no longer expire on a timer, so without this a beam picked
     * up on level one would still be burning through level ten -- a level boundary and a death are
     * the two things that hand the arsenal back, and every level starts you on the stock gun.
     */
    public void returnToSpawn() {
        setPosition(spawnX, spawnY);
        setVelocity(0, 0);
        setLean(Lean.NONE);
        activeEffects.clear();
        firingBeam = false;
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

    /**
     * The hull's extents on screen, which turn with it in a side-view level.
     *
     * The pair below exist because {@code Entity}'s are fixed at construction from the sprite it
     * was built with, and a pilot changes cut between levels rather than being rebuilt. Everything
     * that measures the ship goes through these -- the collision box, the arena clamp, the spawn
     * lane -- so the hull cannot be turned on screen while still being measured upright.
     */
    @Override
    public double width() {
        return facing.horizontal() ? super.height() : super.width();
    }

    @Override
    public double height() {
        return facing.horizontal() ? super.width() : super.height();
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
