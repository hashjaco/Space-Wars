package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/** A drifting hazard. Larger ones take more hits and hurt more. */
public final class Asteroid extends Entity {

    private final int contactDamage;
    private final int scoreValue;
    private int health;

    public Asteroid(Sprite sprite, double x, double y, int health, int contactDamage, int scoreValue) {
        super(sprite, x, y);
        this.health = health;
        this.contactDamage = contactDamage;
        this.scoreValue = scoreValue;
    }

    public void takeDamage(int amount) {
        health -= amount;
        if (health <= 0) {
            kill();
        }
    }

    public int contactDamage() {
        return contactDamage;
    }

    public int scoreValue() {
        return scoreValue;
    }
}
