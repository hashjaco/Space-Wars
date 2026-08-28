package com.hashimjacobs.spacecase.engine;

import java.util.function.IntFunction;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.garage.Upgrade;
import com.hashimjacobs.spacecase.entity.Rocket;

/** Turns one player's intent into ship movement and weapon fire. */
public final class ShipController {

    private static final double TRI_SHOT_SPREAD = 3.4;

    /** Above this share of full horizontal speed the ship banks hard rather than merely tilting. */
    private static final double HARD_BANK_THRESHOLD = 0.85;

    private final PlayerShip ship;
    private final PlayerControls controls;
    private final IntFunction<PadState> sticks;

    /**
     * @param sticks this player's pad reading by player number, or null when nothing but a keyboard
     *               is attached. A stick cannot travel through a {@link javafx.scene.input.KeyCode},
     *               so analog movement needs this side channel; the pad still speaks in keys for
     *               everything else, which is what keeps menus and the garage working.
     */
    public ShipController(PlayerShip ship, PlayerControls controls, IntFunction<PadState> sticks) {
        this.ship = ship;
        this.controls = controls;
        this.sticks = sticks;
    }

    public PlayerShip ship() {
        return ship;
    }

    /** The garage drives each bay with the keys that pilot flies with, so it needs these too. */
    public PlayerControls controls() {
        return controls;
    }

    /**
     * Reads this machine's keyboard and pad, and nothing else, into one tick of {@link Intent}.
     *
     * The half of the old {@code apply} that is local. Everything per-machine is consumed right
     * here: the bindings in {@link #controls}, and the deadzone, analog flag and sensitivity in
     * {@code tuning}. What comes out is a plain vector that means the same thing on every machine,
     * which is what makes it safe to put on a wire.
     */
    public Intent sample(InputState input, StickTuning tuning) {
        boolean firing = controls.anyHeld(input, controls.fire());

        double[] stick = analogTravel(tuning);
        if (stick != null) {
            return new Intent(stick[0], stick[1], firing);
        }

        double dx = 0;
        double dy = 0;
        if (controls.anyHeld(input, controls.up())) {
            dy -= 1;
        }
        if (controls.anyHeld(input, controls.down())) {
            dy += 1;
        }
        if (controls.anyHeld(input, controls.left())) {
            dx -= 1;
        }
        if (controls.anyHeld(input, controls.right())) {
            dx += 1;
        }

        // Normalise so diagonals are not faster than the cardinals.
        if (dx != 0 && dy != 0) {
            double diagonal = Math.sqrt(0.5);
            dx *= diagonal;
            dy *= diagonal;
        }
        return new Intent(dx, dy, firing);
    }

    /**
     * Sample and act, for a player sitting at this machine.
     *
     * Kept as the way local play drives a ship, so the loop and the suite say what they always
     * said. A networked game is the same two steps held apart: {@link #sample} here, {@link #apply}
     * on every machine, one tick later.
     */
    public void apply(InputState input, World world, SoundPlayer sounds, StickTuning tuning) {
        apply(sample(input, tuning), world, sounds);
    }

    /**
     * Acts on an intent, wherever it came from: this keyboard, a touchscreen, or a socket.
     *
     * The half that is simulation. Every machine in a networked game runs this for every player
     * with the same intents in the same tick order, and must reach the same ship.
     */
    public void apply(Intent intent, World world, SoundPlayer sounds) {
        if (ship.isOut()) {
            ship.setVelocity(0, 0);
            ship.setFiringBeam(false);
            return;
        }
        applyMovement(intent, world);
        applyFire(intent.firing(), world, sounds);
    }

    private void applyMovement(Intent intent, World world) {
        double speed = ship.speed();
        ship.setVelocity(intent.moveX() * speed, intent.moveY() * speed);
        // Banking keys off movement across the lane, not off screen-x. In a side view the hull is
        // drawn turned ninety degrees, so the ship's own left and right are world up and down --
        // pushing down produces a ship-local right bank, which after the rotation reads as a
        // downward bank. The rotation and the lean compose correctly and need no new art.
        ship.setLean(leanFor(world.orientation().across(intent.moveX(), intent.moveY())));
    }

    /**
     * This player's left stick as a movement vector, or null to fall back to the digital keys.
     *
     * Returns null for a centred stick as well as for no pad at all, which is what keeps the
     * keyboard working while a controller is plugged in: a resting stick yields to the keys rather
     * than pinning the ship still.
     *
     * Three corrections stand between the raw axes and a ship that handles properly:
     *
     * SDL clamps each axis independently, so a full diagonal reads about 1.41 and would outrun a
     * cardinal push. Scaling back to the unit circle costs one square root a frame and is the
     * difference between analog feeling right and diagonals being a speed exploit.
     *
     * Travel is then measured from the edge of the deadzone rather than from centre. Without that
     * the ship leaps from a standstill to whatever fraction the deadzone sits at -- three tenths of
     * full speed, by default -- with nothing in between, which is precisely the twitch that reads as
     * a stick with no feel to it.
     *
     * Sensitivity finally bends the curve between those ends. It is applied as an exponent, so it
     * changes how quickly speed arrives and never how much of it there is: a full push leaves travel
     * at one, and one raised to any power is one. The ship's own speed, upgrades included, stays the
     * only thing that decides how fast it can go.
     */
    private double[] analogTravel(StickTuning tuning) {
        double deadzone = tuning.deadzone();
        if (sticks == null || !tuning.analog() || deadzone <= 0) {
            return null;
        }
        PadState pad = sticks.apply(ship.playerNumber());
        if (pad == null || !pad.connected()) {
            return null;
        }
        double x = pad.leftStickX();
        double y = pad.leftStickY();
        double magnitude = Math.hypot(x, y);
        if (magnitude <= deadzone) {
            return null;
        }
        if (magnitude > 1) {
            x /= magnitude;
            y /= magnitude;
            magnitude = 1;
        }

        double travel = (magnitude - deadzone) / (1 - deadzone);
        double scaled = Math.pow(travel, 1 / tuning.sensitivity());
        // Back onto the stick's own direction: x and y still carry the raw magnitude, so divide it
        // out before applying the one we actually want.
        double factor = scaled / magnitude;
        // Screen y grows downward and so does the stick's, so the axes pass straight through.
        return new double[] {x * factor, y * factor};
    }

    /**
     * How hard the ship banks, from the share of its movement running across the lane.
     *
     * Keyboard input is digital, so this comes out of the normalisation above for free: holding one
     * direction alone gives the full component and banks hard, while a diagonal splits it and
     * only tilts. An analog stick feeds the same number its own way -- a gentle push tilts, a full
     * sideways push crosses the threshold and banks hard -- so one rule serves both. Movement up
     * and down the lane produces no bank, which is right: a ship does not roll because it
     * accelerated.
     */
    private static PlayerShip.Lean leanFor(double across) {
        if (across == 0) {
            return PlayerShip.Lean.NONE;
        }
        boolean hard = Math.abs(across) > HARD_BANK_THRESHOLD;
        if (across < 0) {
            return hard ? PlayerShip.Lean.HARD_LEFT : PlayerShip.Lean.LEFT;
        }
        return hard ? PlayerShip.Lean.HARD_RIGHT : PlayerShip.Lean.RIGHT;
    }

    /**
     * Weapon precedence: beam, then rockets, then whatever the gun has stacked.
     *
     * The beam is the one weapon that is not a projectile and not gated by the cooldown -- it is a
     * state the ship is in for as long as the trigger is down, and
     * {@code CollisionSystem.resolveBeams} burns whatever is standing in it. The cooldown is still
     * consulted, but only to pace the firing sample: a laser retriggered sixty times a second is
     * not a sound, it is a fault.
     */
    private void applyFire(boolean firing, World world, SoundPlayer sounds) {
        if (ship.hasEffect(PowerUp.Kind.MEGA_LASER)) {
            ship.setFiringBeam(firing);
            if (firing && ship.canFire()) {
                ship.startFireCooldown();
                sounds.play(SoundFx.LASER);
            }
            return;
        }

        ship.setFiringBeam(false);
        if (!firing || !ship.canFire()) {
            return;
        }

        if (ship.hasEffect(PowerUp.Kind.SCYTHE)) {
            ship.startFireCooldown(GameConfig.SCYTHE_FIRE_COOLDOWN
                    - ship.loadout().level(Upgrade.HONE) * GameConfig.UPGRADE_HONE_STEP);
            fireScythe(world);
        } else if (ship.hasEffect(PowerUp.Kind.FLAK)) {
            ship.startFireCooldown(GameConfig.FLAK_FIRE_COOLDOWN);
            fireFlak(world);
        } else if (ship.hasEffect(PowerUp.Kind.NOVA)) {
            ship.startFireCooldown(GameConfig.NOVA_FIRE_COOLDOWN);
            fireNova(world);
        } else if (ship.hasEffect(PowerUp.Kind.ROCKETS)) {
            // The rack shortens the reload but never below its floor, so rockets stay a salvo you
            // wait for rather than becoming the gun you hold down.
            ship.startFireCooldown(Upgrade.rocketCooldownAt(
                    ship.loadout().level(Upgrade.SALVO)));
            fireRocket(world);
        } else {
            ship.startFireCooldown();
            fireSpread(world);
        }
        sounds.play(SoundFx.LASER);
    }

    /**
     * The gun, from one stream to five.
     *
     * A stock shot is the degenerate case of the fan rather than its own method: one stream, no
     * spread, the plain bullet art. Each tri-shot pickup past the first widens it by a stream, so
     * the same loop covers 1, 3, 4 and 5 and there is no arm of it that a new stack count can miss.
     */
    private void fireSpread(World world) {
        int stacks = ship.triStacks();
        int streams = stacks == 0 ? 1 : 2 + stacks;
        for (int i = 0; i < streams; i++) {
            double spread = (i - (streams - 1) / 2.0) * TRI_SHOT_SPREAD;
            Sprite art = streams == 1 ? Sprite.PLAYER_BULLET
                    : spread < 0 ? Sprite.TRI_BULLET_LEFT
                    : spread > 0 ? Sprite.TRI_BULLET_RIGHT
                    : Sprite.TRI_BULLET_UP;
            world.addBullet(bullet(art, spread, GameConfig.BULLET_DAMAGE));
        }
    }

    /**
     * One homing rocket at whatever is closest. A null target is fine -- it flies straight.
     *
     * Shares {@link #muzzle} and {@code damageFor} with the gun rather than a {@link Bullet}, so
     * the nose position, the shot counter and the firepower upgrade all behave identically; only
     * the entity built around them differs.
     */
    /**
     * The scythe: one wide blade across the lane, piercing.
     *
     * Goes through {@link #bullet} like the gun does, so the muzzle, the shot counter and the
     * firepower upgrade all behave identically -- only the art, the speed and the pierce differ.
     */
    private void fireScythe(World world) {
        Bullet blade = bullet(Sprite.SCYTHE_BLADE, 0, GameConfig.SCYTHE_DAMAGE,
                GameConfig.SCYTHE_SPEED);
        world.addBullet(blade.piercing());
    }

    /**
     * The flak: a fan of fused pellets.
     *
     * Every pellet is a separate {@code recordShot} through {@link #bullet}, which is what keeps
     * accuracy honest -- seven rounds are seven chances to hit, not one.
     */
    private void fireFlak(World world) {
        int pellets = GameConfig.FLAK_PELLETS
                + ship.loadout().level(Upgrade.CHOKE) * GameConfig.UPGRADE_CHOKE_STEP;
        for (int i = 0; i < pellets; i++) {
            double spread = (i - (pellets - 1) / 2.0) * GameConfig.FLAK_SPREAD;
            Bullet pellet = bullet(Sprite.FLAK_PELLET, spread, GameConfig.FLAK_DAMAGE,
                    GameConfig.FLAK_SPEED);
            world.addBullet(pellet.withFuse(GameConfig.FLAK_FUSE_TICKS));
        }
    }

    /** The nova: one slow shell that answers for a radius when it lands. */
    private void fireNova(World world) {
        double radius = GameConfig.NOVA_BLAST_RADIUS
                + ship.loadout().level(Upgrade.YIELD) * GameConfig.UPGRADE_YIELD_STEP;
        Bullet shell = bullet(Sprite.NOVA_SHELL, 0, GameConfig.NOVA_DAMAGE, GameConfig.NOVA_SPEED);
        world.addBullet(shell.detonating(radius));
    }

    private void fireRocket(World world) {
        ship.recordShot();
        double[] muzzle = muzzle(Sprite.ROCKET);
        world.addBullet(new Rocket(Sprite.ROCKET, muzzle[0], muzzle[1],
                ship.facing().xDirection() * GameConfig.PLAYER_ROCKET_SPEED,
                ship.facing().yDirection() * GameConfig.PLAYER_ROCKET_SPEED,
                ship, world.nearestEnemy(ship),
                ship.damageFor(GameConfig.PLAYER_ROCKET_DAMAGE),
                GameConfig.PLAYER_ROCKET_TURN_RATE, GameConfig.PLAYER_ROCKET_FUSE_TICKS));
    }

    /**
     * @param spread sideways offset for the tri-shot, perpendicular to whichever way the nose
     *               points -- so the fan turns with the ship instead of always splaying on x
     */
    private Bullet bullet(Sprite sprite, double spread, int damage) {
        return bullet(sprite, spread, damage, GameConfig.BULLET_SPEED);
    }

    /**
     * @param speed how fast the round leaves, for a weapon that does not travel at the gun's rate.
     *              An overload rather than a widened signature so the gun and the tri-shot keep
     *              reading as one call.
     */
    private Bullet bullet(Sprite sprite, double spread, int damage, double speed) {
        // Counted here rather than per trigger pull, so a tri-shot's three projectiles are three
        // chances to hit and accuracy cannot come out above 100%.
        ship.recordShot();
        int dirX = ship.facing().xDirection();
        int dirY = ship.facing().yDirection();
        double[] muzzle = muzzle(sprite);
        double x = muzzle[0];
        double y = muzzle[1];
        double velocityX = dirX * speed - dirY * spread;
        double velocityY = dirY * speed + dirX * spread;
        // Firepower is applied here rather than at the call sites, so every stream of every fan
        // benefits and none of them can be forgotten.
        Bullet created = new Bullet(sprite, x, y, velocityX, velocityY, ship,
                ship.damageFor(damage));
        return created;
    }

    /** Where a round of this size leaves the hull -- the nose, whichever edge that currently is. */
    private double[] muzzle(Sprite sprite) {
        int dirX = ship.facing().xDirection();
        int dirY = ship.facing().yDirection();
        return new double[] {
                ship.centerX() - sprite.width() / 2 + dirX * (ship.width() + sprite.width()) / 2,
                ship.centerY() - sprite.height() / 2 + dirY * (ship.height() + sprite.height()) / 2,
        };
    }
}
