package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/** An AI ship. Drifts down the arena, tracks the nearest player horizontally, and fires on a timer. */
public class EnemyShip extends Entity {

    /** Height at which a boss stops descending and holds station to fight. */
    private static final double BOSS_HOLD_Y = 90;

    private static final double BOSS_DESCENT_SPEED = 0.35;
    private static final double BOSS_TRACK_SPEED = 1.3;

    private final EnemyKind kind;
    private final Boss boss;
    private final int maxHealth;
    private final double trackSpeed;
    private final int scoreValue;
    private int health;
    private int fireCooldown;

    /**
     * An ordinary enemy.
     *
     * The art is passed in rather than read off the archetype: every level fields its own faction, so
     * a scout's stats are fixed while its hull is whatever the current {@code mode.Level} supplies.
     */
    public EnemyShip(EnemyKind kind, Sprite art, double x, double y) {
        super(art, x, y);
        this.kind = kind;
        this.boss = null;
        this.maxHealth = kind.health();
        this.trackSpeed = kind.trackSpeed();
        this.scoreValue = kind.scoreValue();
        this.health = kind.health();
        // Stagger the first shot so a wave spawning together does not fire in unison.
        this.fireCooldown = kind.ordinal() * 7;
        setVelocity(0, kind.descentSpeed());
    }

    /** A level's flagship. Its size, health, score and armament all come from the {@link Boss}. */
    public EnemyShip(Boss boss, double x, double y) {
        super(Sprite.BOSS, x, y, boss.art().width(), boss.art().height());
        this.kind = null;
        this.boss = boss;
        this.maxHealth = boss.health();
        this.trackSpeed = BOSS_TRACK_SPEED;
        this.scoreValue = boss.scoreValue();
        this.health = boss.health();
        setVelocity(0, BOSS_DESCENT_SPEED);
    }

    /**
     * A boss takes station near the top of the arena rather than drifting out of the bottom, where
     * the world would cull it mid-fight -- and, because killing a boss is what advances the level,
     * hand out the next level for free.
     *
     * The clamp lives here rather than in the velocity setter so no caller can skip it. It used to
     * sit in a helper reached only from {@link #trackHorizontally}, which meant a boss that was never
     * driven -- because no player was left alive to track -- kept descending.
     */
    @Override
    public void update() {
        super.update();
        if (isBoss() && y() > BOSS_HOLD_Y) {
            setPosition(x(), BOSS_HOLD_Y);
            setVelocity(velocityX(), 0);
        }
    }

    /** Nudges horizontally toward the target. Returns nothing; movement applies on the next update. */
    public void trackHorizontally(Entity target) {
        double dx = target.centerX() - centerX();
        double step = Math.signum(dx) * trackSpeed;
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

    /** The archetype, or null for a boss. */
    public EnemyKind kind() {
        return kind;
    }

    /** Which flagship this is, or null for an ordinary enemy. */
    public Boss boss() {
        return boss;
    }

    /** Remaining health as a 0..1 fraction of this ship's maximum, for the boss bar. */
    public double remainingHealthFraction() {
        double fraction = Math.max(0, health) / (double) maxHealth;
        return fraction;
    }

    public int scoreValue() {
        return scoreValue;
    }

    public boolean isBoss() {
        boolean flagship = boss != null;
        return flagship;
    }

    /**
     * Enemy archetypes, ordered roughly by threat. Flagships are {@link Boss}, not one of these.
     *
     * Stats only: the hull comes from the level being fought, via {@code Level.enemySprite}.
     */
    public enum EnemyKind {

        SCOUT(20, 1.7, 1.6, 15),
        FIGHTER(40, 1.1, 1.1, 25),
        CRUISER(70, 0.7, 0.8, 45);

        private final int health;
        private final double descentSpeed;
        private final double trackSpeed;
        private final int scoreValue;

        EnemyKind(int health, double descentSpeed, double trackSpeed, int scoreValue) {
            this.health = health;
            this.descentSpeed = descentSpeed;
            this.trackSpeed = trackSpeed;
            this.scoreValue = scoreValue;
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
