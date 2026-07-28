package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;

/** A collectable pickup that drifts down the arena and expires if nobody takes it. */
public final class PowerUp extends Entity {

    private final Kind kind;
    private int remainingLifetime = GameConfig.POWERUP_LIFETIME_TICKS;

    public PowerUp(Kind kind, double x, double y) {
        super(kind.sprite(), x, y);
        this.kind = kind;
        setVelocity(0, GameConfig.POWERUP_DRIFT_SPEED);
    }

    @Override
    public void update() {
        super.update();
        remainingLifetime--;
        if (remainingLifetime <= 0) {
            kill();
        }
    }

    public Kind kind() {
        return kind;
    }

    /** What a pickup does. Timed kinds run for {@code GameConfig.POWERUP_DURATION_TICKS}. */
    public enum Kind {

        TRI_SHOT(Sprite.PICKUP_TRI_SHOT, true),
        MEGA_LASER(Sprite.PICKUP_MEGA_LASER, true),
        SPEED(Sprite.PICKUP_SPEED, true),
        SHIELD(Sprite.PICKUP_SHIELD, true),
        HEALTH(Sprite.PICKUP_HEALTH, false),
        EXTRA_LIFE(Sprite.PICKUP_EXTRA_LIFE, false);

        private final Sprite sprite;
        private final boolean timed;

        Kind(Sprite sprite, boolean timed) {
            this.sprite = sprite;
            this.timed = timed;
        }

        public Sprite sprite() {
            return sprite;
        }

        /** True when the effect lasts for a while; false when it applies once and is consumed. */
        public boolean timed() {
            return timed;
        }
    }
}
