package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;

/** Turns held keys into ship movement and weapon fire for one player. */
public final class ShipController {

    private static final double TRI_SHOT_SPREAD = 3.4;

    /** Above this share of full horizontal speed the ship banks hard rather than merely tilting. */
    private static final double HARD_BANK_THRESHOLD = 0.85;

    private final PlayerShip ship;
    private final PlayerControls controls;

    public ShipController(PlayerShip ship, PlayerControls controls) {
        this.ship = ship;
        this.controls = controls;
    }

    public void apply(InputState input, World world, SoundPlayer sounds) {
        if (ship.isOut()) {
            ship.setVelocity(0, 0);
            return;
        }
        applyMovement(input);
        applyFire(input, world, sounds);
    }

    private void applyMovement(InputState input) {
        boolean up = controls.anyHeld(input, controls.up());
        boolean down = controls.anyHeld(input, controls.down());
        boolean left = controls.anyHeld(input, controls.left());
        boolean right = controls.anyHeld(input, controls.right());

        double speed = ship.speed();
        double dx = 0;
        double dy = 0;
        if (up) {
            dy -= 1;
        }
        if (down) {
            dy += 1;
        }
        if (left) {
            dx -= 1;
        }
        if (right) {
            dx += 1;
        }

        // Normalise so diagonals are not faster than the cardinals.
        if (dx != 0 && dy != 0) {
            double diagonal = Math.sqrt(0.5);
            dx *= diagonal;
            dy *= diagonal;
        }
        ship.setVelocity(dx * speed, dy * speed);
        ship.setLean(leanFor(dx));
    }

    /**
     * How hard the ship banks, from the horizontal share of its movement.
     *
     * Input is digital, so this comes out of the normalisation above for free: holding left alone
     * gives the full dx and banks hard, while a diagonal splits it and only tilts.
     */
    private static PlayerShip.Lean leanFor(double dx) {
        if (dx == 0) {
            return PlayerShip.Lean.NONE;
        }
        boolean hard = Math.abs(dx) > HARD_BANK_THRESHOLD;
        if (dx < 0) {
            return hard ? PlayerShip.Lean.HARD_LEFT : PlayerShip.Lean.LEFT;
        }
        return hard ? PlayerShip.Lean.HARD_RIGHT : PlayerShip.Lean.RIGHT;
    }

    private void applyFire(InputState input, World world, SoundPlayer sounds) {
        boolean firing = controls.anyHeld(input, controls.fire());
        if (!firing || !ship.canFire()) {
            return;
        }
        ship.startFireCooldown();

        if (ship.hasEffect(PowerUp.Kind.MEGA_LASER)) {
            fireMega(world);
        } else if (ship.hasEffect(PowerUp.Kind.TRI_SHOT)) {
            fireTriShot(world);
        } else {
            fireSingle(world);
        }
        sounds.play(SoundFx.LASER);
    }

    private void fireSingle(World world) {
        Bullet bullet = bullet(Sprite.PLAYER_BULLET, 0, GameConfig.BULLET_DAMAGE);
        world.addBullet(bullet);
    }

    private void fireMega(World world) {
        Bullet bullet = bullet(Sprite.MEGA_BULLET, 0, GameConfig.MEGA_BULLET_DAMAGE);
        world.addBullet(bullet);
    }

    private void fireTriShot(World world) {
        Bullet left = bullet(Sprite.TRI_BULLET_LEFT, -TRI_SHOT_SPREAD, GameConfig.BULLET_DAMAGE);
        Bullet centre = bullet(Sprite.TRI_BULLET_UP, 0, GameConfig.BULLET_DAMAGE);
        Bullet right = bullet(Sprite.TRI_BULLET_RIGHT, TRI_SHOT_SPREAD, GameConfig.BULLET_DAMAGE);
        world.addBullet(left);
        world.addBullet(centre);
        world.addBullet(right);
    }

    private Bullet bullet(Sprite sprite, double velocityX, int damage) {
        // Counted here rather than per trigger pull, so a tri-shot's three projectiles are three
        // chances to hit and accuracy cannot come out above 100%.
        ship.recordShot();
        int direction = ship.facing().yDirection();
        double x = ship.centerX() - sprite.width() / 2;
        // Emerge from the nose, which is the top edge facing up and the bottom edge facing down.
        double y = direction < 0 ? ship.y() - sprite.height() : ship.y() + ship.height();
        double velocityY = GameConfig.BULLET_SPEED * direction;
        Bullet created = new Bullet(sprite, x, y, velocityX, velocityY, ship, damage);
        return created;
    }
}
