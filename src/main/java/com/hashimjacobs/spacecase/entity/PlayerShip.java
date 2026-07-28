package com.hashimjacobs.spacecase.entity;

import java.util.EnumMap;
import java.util.Map;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;

/** A player-controlled ship: health, lives, score, and whatever power-ups are currently running. */
public final class PlayerShip extends Entity {

    private final int playerNumber;
    private final Skin skin;
    private final Facing facing;
    private final double spawnX;
    private final double spawnY;

    private final Map<PowerUp.Kind, Integer> activeEffects = new EnumMap<>(PowerUp.Kind.class);

    private int health = GameConfig.PLAYER_HEALTH;
    private int lives = GameConfig.PLAYER_LIVES;
    private int score;
    private int enemiesKilled;
    private int asteroidsDestroyed;
    private int fireCooldown;
    private int invulnerableTicks;
    private int hitFlashTicks;
    private Lean lean = Lean.NONE;

    public PlayerShip(int playerNumber, Facing facing, double spawnX, double spawnY) {
        super(playerNumber == 1 ? Sprite.P1_STRAIGHT : Sprite.P2_STRAIGHT, spawnX, spawnY);
        this.playerNumber = playerNumber;
        this.skin = playerNumber == 1 ? Skin.PLAYER_ONE : Skin.PLAYER_TWO;
        this.facing = facing;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
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

    public void startFireCooldown() {
        fireCooldown = GameConfig.PLAYER_FIRE_COOLDOWN;
    }

    /**
     * Applies damage unless shielded or still invulnerable from a respawn.
     * Returns true when the hit cost the player a life.
     */
    public boolean takeDamage(int amount) {
        if (invulnerableTicks > 0 || hasEffect(PowerUp.Kind.SHIELD)) {
            return false;
        }
        health -= amount;
        hitFlashTicks = 18;
        if (health > 0) {
            return false;
        }
        lives--;
        respawn();
        return true;
    }

    private void respawn() {
        health = GameConfig.PLAYER_HEALTH;
        setPosition(spawnX, spawnY);
        setVelocity(0, 0);
        invulnerableTicks = GameConfig.PLAYER_INVULNERABLE_TICKS;
        activeEffects.clear();
    }

    public void collect(PowerUp.Kind kind) {
        switch (kind) {
            case HEALTH -> health = GameConfig.PLAYER_HEALTH;
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

    public double speed() {
        double speed = hasEffect(PowerUp.Kind.SPEED)
                ? GameConfig.PLAYER_SPEED_BOOSTED
                : GameConfig.PLAYER_SPEED;
        return speed;
    }

    /** Sets which way the ship is leaning so the renderer can pick the banked sprite. */
    public void setLean(Lean lean) {
        this.lean = lean;
        Sprite next = skin.spriteFor(lean, hitFlashTicks > 0);
        setSprite(next);
    }

    /** Refreshes the sprite after a hit flash starts or ends, without changing the lean. */
    public void refreshSprite() {
        setLean(lean);
    }

    public boolean isOut() {
        boolean out = lives <= 0;
        return out;
    }

    public boolean isInvulnerable() {
        boolean invulnerable = invulnerableTicks > 0;
        return invulnerable;
    }

    public int playerNumber() {
        return playerNumber;
    }

    public Facing facing() {
        return facing;
    }

    public int health() {
        return health;
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

    /** Horizontal banking, which selects the ship's sprite. */
    public enum Lean {
        LEFT, NONE, RIGHT
    }

    /** Per-player sprite sets, including the scorched variants shown briefly after a hit. */
    private enum Skin {

        PLAYER_ONE(Sprite.P1_STRAIGHT, Sprite.P1_LEFT, Sprite.P1_RIGHT,
                Sprite.P1_STRAIGHT_HIT, Sprite.P1_LEFT_HIT, Sprite.P1_RIGHT_HIT),
        PLAYER_TWO(Sprite.P2_STRAIGHT, Sprite.P2_LEFT, Sprite.P2_RIGHT,
                Sprite.P2_STRAIGHT_HIT, Sprite.P2_LEFT_HIT, Sprite.P2_RIGHT_HIT);

        private final Sprite straight;
        private final Sprite left;
        private final Sprite right;
        private final Sprite straightHit;
        private final Sprite leftHit;
        private final Sprite rightHit;

        Skin(Sprite straight, Sprite left, Sprite right,
             Sprite straightHit, Sprite leftHit, Sprite rightHit) {
            this.straight = straight;
            this.left = left;
            this.right = right;
            this.straightHit = straightHit;
            this.leftHit = leftHit;
            this.rightHit = rightHit;
        }

        Sprite spriteFor(Lean lean, boolean hit) {
            Sprite chosen = switch (lean) {
                case LEFT -> hit ? leftHit : left;
                case RIGHT -> hit ? rightHit : right;
                case NONE -> hit ? straightHit : straight;
            };
            return chosen;
        }
    }
}
