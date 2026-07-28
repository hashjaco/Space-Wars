package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/** A projectile. {@code owner} is null for enemy fire and identifies the shooter otherwise. */
public final class Bullet extends Entity {

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
}
