package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/** An AI ship. Drifts down the arena, tracks the nearest player horizontally, and fires on a timer. */
public class EnemyShip extends Entity {

    private final EnemyKind kind;
    private int health;
    private int fireCooldown;

    public EnemyShip(EnemyKind kind, double x, double y) {
        super(kind.sprite(), x, y);
        this.kind = kind;
        this.health = kind.health();
        // Stagger the first shot so a wave spawning together does not fire in unison.
        this.fireCooldown = kind.ordinal() * 7;
        setVelocity(0, kind.descentSpeed());
    }

    /** Nudges horizontally toward the target. Returns nothing; movement applies on the next update. */
    public void trackHorizontally(Entity target) {
        double dx = target.centerX() - centerX();
        double step = Math.signum(dx) * kind.trackSpeed();
        setVelocity(step, velocityY());
    }

    /** Counts down the weapon timer and reports whether the ship may fire this tick. */
    public boolean tickWeapon(int cooldownTicks) {
        if (fireCooldown > 0) {
            fireCooldown--;
            return false;
        }
        fireCooldown = cooldownTicks;
        return true;
    }

    public void takeDamage(int amount) {
        health -= amount;
        if (health <= 0) {
            kill();
        }
    }

    public EnemyKind kind() {
        return kind;
    }

    /** Remaining health as a 0..1 fraction of this archetype's maximum, for the boss bar. */
    public double remainingHealthFraction() {
        double fraction = Math.max(0, health) / (double) kind.health();
        return fraction;
    }

    public int scoreValue() {
        int value = kind.scoreValue();
        return value;
    }

    public boolean isBoss() {
        boolean boss = kind == EnemyKind.BOSS;
        return boss;
    }

    /** Enemy archetypes, ordered roughly by threat. */
    public enum EnemyKind {

        SCOUT(Sprite.ENEMY_SCOUT, 20, 1.7, 1.6, 15),
        FIGHTER(Sprite.ENEMY_FIGHTER, 40, 1.1, 1.1, 25),
        CRUISER(Sprite.ENEMY_CRUISER, 70, 0.7, 0.8, 45),
        BOSS(Sprite.BOSS, 600, 0.35, 1.3, 500);

        private final Sprite sprite;
        private final int health;
        private final double descentSpeed;
        private final double trackSpeed;
        private final int scoreValue;

        EnemyKind(Sprite sprite, int health, double descentSpeed, double trackSpeed, int scoreValue) {
            this.sprite = sprite;
            this.health = health;
            this.descentSpeed = descentSpeed;
            this.trackSpeed = trackSpeed;
            this.scoreValue = scoreValue;
        }

        public Sprite sprite() {
            return sprite;
        }

        public int health() {
            return health;
        }

        public double descentSpeed() {
            return descentSpeed;
        }

        public double trackSpeed() {
            return trackSpeed;
        }

        public int scoreValue() {
            return scoreValue;
        }
    }
}
