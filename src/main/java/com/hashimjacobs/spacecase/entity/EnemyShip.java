package com.hashimjacobs.spacecase.entity;

import java.util.ArrayList;
import java.util.List;

import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.asset.Sprite;

/** An AI ship. Drifts down the arena, tracks the nearest player across it, and fires on a timer. */
public class EnemyShip extends Entity {

    /** How far in a boss advances before it stops and holds station to fight. */
    private static final double BOSS_HOLD_DEPTH = 90;

    private static final double BOSS_DESCENT_SPEED = 0.35;
    private static final double BOSS_TRACK_SPEED = 1.3;

    /** Grace before a flagship's first rocket salvo, so it does not open with one on arrival. */
    private static final int ROCKET_WARMUP_TICKS = 240;

    /**
     * Share of a multi-part flagship's health carried by its parts rather than its body.
     *
     * The tuning knob for the whole fight. Raise it and the heads outlast the torso; lower it and
     * they are a formality on the way to the real target.
     * ponytail: one constant rather than per-boss data, until a second multi-part boss disagrees.
     */
    private static final double PART_HEALTH_SHARE = 0.45;

    private final EnemyKind kind;
    private final Boss boss;
    private final int maxHealth;
    private final double trackSpeed;
    private final int scoreValue;
    private final double scale;
    /** Parts of a multi-part flagship. Empty for every other ship, so every loop over it is free. */
    private final List<EnemyShip> parts = new ArrayList<>();
    private Orientation orientation = Orientation.TOP_DOWN;
    private int health;
    private int fireCooldown;
    private int rocketCooldown = ROCKET_WARMUP_TICKS;

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
        this.scale = 1;
        this.health = kind.health();
        // Stagger the first shot so a wave spawning together does not fire in unison.
        this.fireCooldown = kind.ordinal() * 7;
        setVelocity(0, kind.descentSpeed());
    }

    /** A level's flagship at its authored strength. */
    public EnemyShip(Boss boss, double x, double y) {
        this(boss, x, y, 1);
    }

    /**
     * A level's flagship. Its size, score and armament all come from the {@link Boss}.
     *
     * @param scale how much tougher than authored this one is, from
     *              {@code prefs.Difficulty.bossScale}. Multiplies health here and fire rate and
     *              projectile damage in {@code engine.EnemyWeapons}. Score is deliberately left
     *              unscaled: paying more for the same flagship is a separate decision from making
     *              it harder, and the debrief already pays a bounty that rises with the level.
     */
    public EnemyShip(Boss boss, double x, double y, double scale) {
        this(boss, x, y, scale, boss.art().width(), boss.art().height(),
                (int) Math.round(boss.health() * scale * bodyShare(boss)), boss.scoreValue());
        setVelocity(0, BOSS_DESCENT_SPEED);
        // A multi-part flagship builds its own parts, so no caller can construct half of one.
        // They are added to the world by SpawnDirector, which is the only thing that can.
        for (int index = 0; index < boss.heads(); index++) {
            int share = (int) Math.round(boss.health() * scale * PART_HEALTH_SHARE / boss.heads());
            parts.add(new BossHead(this, index, boss.heads(), share));
        }
    }

    /** The torso's cut of a multi-part flagship's health; all of it for an ordinary one. */
    private static double bodyShare(Boss boss) {
        return boss.heads() == 0 ? 1 : 1 - PART_HEALTH_SHARE;
    }

    /** One part of a larger flagship: its own box and health, sharing the parent's {@link Boss}. */
    protected EnemyShip(Boss boss, double x, double y, double scale,
                        double width, double height, int health, int scoreValue) {
        super(Sprite.BOSS, x, y, width, height);
        this.kind = null;
        this.boss = boss;
        this.scale = scale;
        this.maxHealth = health;
        this.trackSpeed = BOSS_TRACK_SPEED;
        this.scoreValue = scoreValue;
        this.health = health;
    }

    /**
     * A boss takes station near the top of the arena rather than drifting out of the bottom, where
     * the world would cull it mid-fight -- and, because killing a boss is what advances the level,
     * hand out the next level for free.
     *
     * The clamp lives here rather than in the velocity setter so no caller can skip it. It used to
     * sit in a helper reached only from {@link #trackAcross}, which meant a boss that was never
     * driven -- because no player was left alive to track -- kept descending.
     */
    @Override
    public void update() {
        super.update();
        if (isBoss() && orientation.depth(x(), y(), width(), height()) > BOSS_HOLD_DEPTH) {
            // Stop advancing but keep whatever sideways drift the tracker gave us.
            // ponytail: leaves up to one tick of overshoot (0.35px, once) where the old code
            // snapped the position back. Use Orientation.atX/atY if that ever shows.
            double across = orientation.across(velocityX(), velocityY());
            setVelocity(orientation.vx(0, across), orientation.vy(0, across));
        }
    }

    /**
     * Nudges across the lane toward the target, keeping the current advance.
     *
     * Sideways in a top-down level, up and down in a side view -- the same manoeuvre either way,
     * which is why this is one method rather than two.
     */
    public void trackAcross(Entity target) {
        double across = orientation.across(target.centerX() - centerX(),
                target.centerY() - centerY());
        double step = Math.signum(across) * trackSpeed;
        double advance = orientation.along(velocityX(), velocityY());
        setVelocity(orientation.vx(advance, step), orientation.vy(advance, step));
    }

    /** Whether this ship has come far enough into the arena to be worth driving or drawing. */
    public boolean hasEntered() {
        double extent = orientation.alongExtent(width(), height());
        return orientation.depth(x(), y(), width(), height()) > -extent / 2;
    }

    /**
     * Points this ship down the lane of the level it is entering.
     *
     * Stamped by {@link com.hashimjacobs.spacecase.engine.World#addEnemy} rather than taken as a
     * constructor argument: that catches escorts a boss calls in mid-fight for free, and leaves
     * every existing caller -- and every test that builds a ship directly -- on the top-down
     * default behaving exactly as before.
     */
    public void enter(Orientation orientation) {
        this.orientation = orientation;
        double advance = isBoss() ? BOSS_DESCENT_SPEED : kind.descentSpeed();
        setVelocity(orientation.vx(advance, 0), orientation.vy(advance, 0));
    }

    public Orientation orientation() {
        return orientation;
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

    /**
     * The rocket salvo's own timer, run alongside {@link #tickWeapon} rather than instead of it.
     *
     * A flagship has two weapons at once: the pattern its {@code BossPhase} describes, and this. A
     * phase could not express that, since only one phase is active at a time.
     */
    public boolean tickRocket(int cooldownTicks) {
        if (rocketCooldown > 0) {
            rocketCooldown--;
            return false;
        }
        rocketCooldown = cooldownTicks;
        return true;
    }

    /** How much tougher than authored this ship is; always 1 for an ordinary enemy. */
    public double scale() {
        return scale;
    }

    /**
     * Applies damage, unless a live part is shielding this ship.
     *
     * A hydra's torso takes nothing while a head still bites, which is what makes the heads the
     * fight rather than decoration. An ordinary ship has no parts, so the loop costs nothing.
     */
    public void takeDamage(int amount) {
        for (EnemyShip part : parts) {
            if (part.isAlive()) {
                return;
            }
        }
        health -= amount;
        if (health <= 0) {
            kill();
        }
    }

    /** True for a head or segment of a larger flagship, false for the flagship itself. */
    public boolean isBossPart() {
        return false;
    }

    /** The parts shielding this ship, empty for everything but a multi-part flagship. */
    public List<EnemyShip> parts() {
        return parts;
    }

    /** Which pattern this ship is firing. Overridden by parts that fight on their own clock. */
    public BossPhase phase() {
        return boss.phaseFor(remainingHealthFraction());
    }

    /** The frames this ship animates through: a part wears its own art, not the flagship's. */
    public BossArt bossArt() {
        return isBossPart() ? boss.headArt() : boss.art();
    }

    /**
     * Offsets the first shot and the first salvo.
     *
     * Three heads built in the same tick would otherwise fire in perfect unison, which reads as
     * one weapon rather than three. Same trick the EnemyKind constructor uses for a wave.
     */
    protected void stagger(int weaponTicks, int secondaryTicks) {
        fireCooldown += weaponTicks;
        rocketCooldown += secondaryTicks;
    }

    /** The archetype, or null for a boss. */
    public EnemyKind kind() {
        return kind;
    }

    /** Which flagship this is, or null for an ordinary enemy. */
    public Boss boss() {
        return boss;
    }

    /**
     * Remaining health as a 0..1 fraction, counting any parts, for the boss bar and the phase clock.
     *
     * Summing the parts does two jobs at once: the bar starts draining from the first shot even
     * though the torso is untouchable, and the torso's own pattern escalates as its heads die --
     * without a second state machine to track how many are left.
     */
    public double remainingHealthFraction() {
        int live = Math.max(0, health);
        int max = maxHealth;
        for (EnemyShip part : parts) {
            live += Math.max(0, part.health);
            max += part.maxHealth;
        }
        return live / (double) max;
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
