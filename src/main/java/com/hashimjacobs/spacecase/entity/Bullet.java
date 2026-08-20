package com.hashimjacobs.spacecase.entity;

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

    public Bullet(Sprite sprite, double x, double y, double velocityX, double velocityY,
                  PlayerShip owner, int damage) {
        super(sprite, x, y);
        this.owner = owner;
        this.damage = damage;
        setVelocity(velocityX, velocityY);
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
