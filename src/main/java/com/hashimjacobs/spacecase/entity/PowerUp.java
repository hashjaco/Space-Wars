package com.hashimjacobs.spacecase.entity;

import java.util.Random;

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

    /**
     * What a pickup does.
     *
     * Nothing here is timed. An effect runs until the pilot loses a life, which is what
     * {@code PlayerShip.respawn()} takes away -- so the cost of a mistake is the arsenal, not a
     * countdown nobody was watching.
     */
    public enum Kind {

        TRI_SHOT(Sprite.PICKUP_TRI_SHOT),
        MEGA_LASER(Sprite.PICKUP_MEGA_LASER),
        ROCKETS(Sprite.PICKUP_ROCKET),
        SPEED(Sprite.PICKUP_SPEED),
        SHIELD(Sprite.PICKUP_SHIELD),
        HEALTH(Sprite.PICKUP_HEALTH),
        EXTRA_LIFE(Sprite.PICKUP_EXTRA_LIFE),

        /**
         * The three weapons that come off one archetype each rather than out of the common table.
         *
         * Kept out of {@link #COMMON} deliberately, and not only for rarity: that array's length is
         * the modulus {@link #randomCommon} draws against, so adding to it re-rolls every seeded
         * drop assertion in the suite for no gain. {@code engine.CollisionSystem.rollKind} hands
         * these out per hull instead, the way the beam already was.
         */
        SCYTHE(Sprite.PICKUP_SCYTHE),
        FLAK(Sprite.PICKUP_FLAK),
        NOVA(Sprite.PICKUP_NOVA);

        /**
         * What anything may drop. The beam is deliberately not in here.
         *
         * It is the strongest weapon in the game -- it pierces, it never runs out and it does not
         * expire -- so it cannot be one uniform pick in six off every scout that dies. Only a heavy
         * hull or a flagship carries one, and {@code engine.CollisionSystem} rolls for that
         * separately and at worse odds than any of these.
         */
        private static final Kind[] COMMON =
                {TRI_SHOT, ROCKETS, SPEED, SHIELD, HEALTH, EXTRA_LIFE};

        private final Sprite sprite;

        Kind(Sprite sprite) {
            this.sprite = sprite;
        }

        public Sprite sprite() {
            return sprite;
        }

        /** One of everything an ordinary kill, or an arena with nothing to kill, can hand out. */
        public static Kind randomCommon(Random random) {
            return COMMON[random.nextInt(COMMON.length)];
        }
    }
}
