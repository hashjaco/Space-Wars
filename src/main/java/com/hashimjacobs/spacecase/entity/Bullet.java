package com.hashimjacobs.spacecase.entity;

import java.util.HashSet;
import java.util.Set;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * A projectile. {@code owner} is null for enemy fire and identifies the shooter otherwise.
 *
 * Not final because {@link Rocket} is one: it is a bullet in every way the collision, sweep and
 * render paths care about, and differs only in steering itself on the way in.
 */
public class Bullet extends Entity {

    private final PlayerShip owner;
    private final int damage;

    /**
     * What a piercing round has already burned, or null for a round that stops at the first thing.
     *
     * Held per shot rather than checked per tick, because "pierces" and "hits the same ship sixty
     * times a second" are one line apart. A non-piercing bullet allocates nothing.
     */
    private Set<Entity> pierced;

    /** How far a detonating round reaches when it dies, in pixels. Zero for one that does not. */
    private double blastRadius;

    /** Ticks before a short-range round expires on its own. Zero means it flies until it leaves. */
    private int fuse;

    public Bullet(Sprite sprite, double x, double y, double velocityX, double velocityY,
                  PlayerShip owner, int damage) {
        super(sprite, x, y);
        this.owner = owner;
        this.damage = damage;
        setVelocity(velocityX, velocityY);
    }

    /**
     * Makes this round pass through what it hits, burning each thing exactly once.
     *
     * Returns itself so a fire method reads as one expression. The set is the whole mechanism: the
     * collision pass asks {@link #canStillHit} before it damages anything and {@link #recordPierce}
     * after, and never kills a piercing round.
     */
    public Bullet piercing() {
        this.pierced = new HashSet<>();
        return this;
    }

    /** Detonates for {@code radius} pixels when it dies against something. */
    public Bullet detonating(double radius) {
        this.blastRadius = radius;
        return this;
    }

    /** Expires after {@code ticks}, which is what gives a short-range round its range. */
    public Bullet withFuse(int ticks) {
        this.fuse = ticks;
        return this;
    }

    public boolean pierces() {
        boolean through = pierced != null;
        return through;
    }

    public double blastRadius() {
        return blastRadius;
    }

    /** False once this round has already burned that target, so a pierce cannot re-hit it. */
    public boolean canStillHit(Entity target) {
        boolean fresh = pierced == null || !pierced.contains(target);
        return fresh;
    }

    public void recordPierce(Entity target) {
        if (pierced != null) {
            pierced.add(target);
        }
    }

    @Override
    public void update() {
        super.update();
        // Only a fused round counts down. Everything else leaves through the arena edge, which is
        // what World.killWhatLeftTheArena is for.
        if (fuse > 0 && --fuse == 0) {
            kill();
        }
    }

    /** The player who fired this, or null when an enemy fired it. */
    public PlayerShip owner() {
        return owner;
    }

    public boolean firedByPlayer() {
        boolean fromPlayer = owner != null;
        return fromPlayer;
    }

    public int damage() {
        return damage;
    }

    /**
     * Degrees to turn the nose-up art by so it points where the round is actually going.
     *
     * Read off the velocity rather than off a level's {@link Orientation} so one expression covers
     * every case: the four axis-aligned ones, the tri-shot's fanned rounds, and a {@link Rocket}
     * that changes heading every tick.
     *
     * Note the argument order. This is {@code atan2(vx, -vy)}, not the textbook
     * {@code atan2(dy, dx)}: zero degrees has to mean straight up, because that is how the
     * projectiles are drawn, and the canvas turns clockwise. See Rocket's own note on the two
     * conventions in play here.
     */
    public double headingDegrees() {
        return Math.toDegrees(Math.atan2(velocityX(), -velocityY()));
    }
}
