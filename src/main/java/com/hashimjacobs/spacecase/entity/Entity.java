package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * Anything that occupies space in the arena.
 *
 * Position is the top-left corner. Size is stored explicitly rather than read back from the sprite
 * image, so collision and spatial indexing work without a decoded image and can be unit tested
 * without starting the JavaFX toolkit.
 */
public abstract class Entity {

    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private final double width;
    private final double height;
    private Sprite sprite;
    private boolean alive = true;

    protected Entity(Sprite sprite, double x, double y) {
        this(sprite, x, y, sprite.width(), sprite.height());
    }

    protected Entity(Sprite sprite, double x, double y, double width, double height) {
        this.sprite = sprite;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void update() {
        x += velocityX;
        y += velocityY;
    }

    public boolean intersects(Entity other) {
        boolean overlapping = x < other.x + other.width
                && x + width > other.x
                && y < other.y + other.height
                && y + height > other.y;
        return overlapping;
    }

    /**
     * Marks the entity for removal. Nothing is removed from any collection here -- the world sweeps
     * dead entities once per frame, which is what keeps collision handling from mutating the lists
     * it is iterating.
     */
    public void kill() {
        alive = false;
    }

    public boolean isAlive() {
        return alive;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double centerX() {
        double center = x + width / 2;
        return center;
    }

    public double centerY() {
        double center = y + height / 2;
        return center;
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public void setVelocity(double velocityX, double velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Sprite sprite() {
        return sprite;
    }

    protected void setSprite(Sprite sprite) {
        this.sprite = sprite;
    }
}
