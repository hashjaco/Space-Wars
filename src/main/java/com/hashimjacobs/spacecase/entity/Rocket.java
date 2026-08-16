package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * A flagship's secondary weapon: slow, heavy, and it follows you.
 *
 * Every other projectile in the game flies the heading it was fired on. This one turns a little
 * each tick toward the player it was launched at, which is what makes a boss fight about moving
 * rather than about finding a safe column and staying in it.
 *
 * The turn rate is the whole balance of the thing. Too high and it is unavoidable; at the tuned
 * {@code GameConfig.BOSS_ROCKET_TURN_RATE} a player flying flat out can pull it into a wide arc and
 * outrun it, which is the intended answer.
 */
public final class Rocket extends Bullet {

    /**
     * Ticks before a rocket that never connected burns out.
     *
     * Not optional. {@code World.killWhatLeftTheArena} culls bullets that leave the top or bottom
     * of the arena, and a rocket circling its target never does, so without a fuse a missed salvo
     * would orbit forever and accumulate one pair per salvo for the rest of the fight.
     */
    public static final int FUSE_TICKS = 420;

    private final PlayerShip target;
    private final double speed;
    private final double turnRate;
    private int fuse;

    /**
     * @param target   who it steers toward; it flies straight if this is null or already out
     * @param speed    constant, and stays constant: turning must not accelerate it
     * @param turnRate the most it may turn in one tick, in radians
     */
    public Rocket(double x, double y, PlayerShip target, double speed, int damage,
                  double turnRate) {
        this(Sprite.BOSS_ROCKET, x, y, target, speed, damage, turnRate, FUSE_TICKS);
    }

    /**
     * @param art        what it looks like; the renderer tints its halo from this
     * @param fuseTicks  how long before it burns out, which is also the cap on how many can be in
     *                   the air at once: roughly the fuse divided by the interval between launches
     */
    public Rocket(Sprite art, double x, double y, PlayerShip target, double speed, int damage,
                  double turnRate, int fuseTicks) {
        super(art, x, y, 0, speed, null, damage);
        this.target = target;
        this.speed = speed;
        this.turnRate = turnRate;
        this.fuse = fuseTicks;
    }

    @Override
    public void update() {
        steer();
        super.update();
        fuse--;
        if (fuse <= 0) {
            kill();
        }
    }

    /**
     * Turns toward the target by at most {@code turnRate}, then renormalises to constant speed.
     *
     * Beware the angle convention: this uses the standard {@code atan2(dy, dx)}, where zero points
     * along positive x. {@code engine.EnemyWeapons.centreAngleFor} uses {@code atan2(dx, dy)} for
     * the phase patterns, where zero points straight down. They are not interchangeable.
     */
    private void steer() {
        if (target == null || target.isOut()) {
            return;
        }
        double desired = Math.atan2(target.centerY() - centerY(), target.centerX() - centerX());
        double current = Math.atan2(velocityY(), velocityX());
        // Wrapping the difference through sin/cos takes the shortest way round, so a rocket at
        // -179 degrees turns three degrees to reach 178 rather than the long way through zero.
        double delta = Math.atan2(Math.sin(desired - current), Math.cos(desired - current));
        double clamped = Math.max(-turnRate, Math.min(turnRate, delta));
        double heading = current + clamped;
        setVelocity(Math.cos(heading) * speed, Math.sin(heading) * speed);
    }
}
