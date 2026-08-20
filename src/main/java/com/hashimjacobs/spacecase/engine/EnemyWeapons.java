package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.Rocket;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * Enemy firing, kept out of {@link GameLoop} so the loop stays a readable sequence of phases.
 *
 * Ordinary enemies fire one shot straight down. A boss fires the pattern its {@link BossPhase}
 * describes, and which phase that is shifts as its health falls.
 */
final class EnemyWeapons {

    /** Escorts a SPAWNER boss calls in per volley. */
    private static final int MINIONS_PER_VOLLEY = 2;

    /**
     * Ceiling on enemies while a spawner is working.
     *
     * The difficulty cap is enforced in {@link SpawnDirector}, which knows nothing about bosses
     * calling in help, so without a limit here a spawner fills the arena unopposed.
     */
    private static final int MAX_ENEMIES_WITH_MINIONS = 12;

    /** Floor on a scaled boss cooldown, so a late loop cannot turn a pattern into a solid wall. */
    private static final int MIN_BOSS_COOLDOWN_TICKS = 4;

    /**
     * How much slower one head fires than a whole flagship would.
     *
     * A phase's cooldown was tuned for a boss with one mouth. Three heads firing it unmodified put
     * two and a half times as many shots in the air as any other fight in the game -- measured at
     * fifty-six against the Sentinel's twenty-two. Slightly less than the head count, so the hydra
     * is still the heaviest barrage in the run without being a wall.
     */
    private static final double PART_COOLDOWN_FACTOR = 2.4;

    /** Ceiling on how far scaling may speed boss projectiles up. */
    private static final double SPEED_SCALE_CAP = 1.5;

    /** Ticks between rocket salvoes at scale 1, and the floor once scaling shortens it. */
    private static final int ROCKET_COOLDOWN_TICKS = 210;
    private static final int MIN_ROCKET_COOLDOWN_TICKS = 60;

    private static final int ROCKETS_PER_SALVO = 2;

    /**
     * How long a ball of acid lasts.
     *
     * Shorter than a rocket's fuse, and that is the throttle on how many can be in the air: three
     * heads spitting on a staggered timer put roughly three on screen at once. Lengthen it and the
     * arena fills.
     */
    private static final int ACID_FUSE_TICKS = 240;

    private EnemyWeapons() {
    }

    /**
     * Runs one ship's weapons for a tick.
     *
     * An ordinary enemy has one. A flagship has two: the pattern its {@code BossPhase} describes,
     * and a rocket salvo on a much longer timer that runs whichever phase it is in. Both live behind
     * this one entry point so {@link GameLoop#driveEnemies} stays a single call.
     */
    static void driveWeapons(World world, EnemyShip enemy, PlayerShip target, Level level,
                             int difficultyCooldown, SoundPlayer sounds) {
        // The sweep usually clears the dead before this runs, but not always: a hydra head is
        // killed by its own dying torso from inside World.update, which is earlier in the same
        // frame. Without this it gets a parting shot.
        if (!enemy.isAlive()) {
            return;
        }
        if (enemy.tickWeapon(cooldownFor(enemy, difficultyCooldown))) {
            fire(world, enemy, target, level);
            // Ordinary enemies stay silent, as they always have; a spawner is venting escorts
            // rather than shooting, so a machine gun over it would be describing the wrong thing.
            if (enemy.isBoss() && enemy.phase() != BossPhase.SPAWNER) {
                sounds.play(SoundFx.BOSS_GUN);
            }
        }
        int salvo = secondarySalvoFor(enemy);
        if (salvo == 0 || target == null) {
            return;
        }
        int rocketCooldown = Math.max(MIN_ROCKET_COOLDOWN_TICKS,
                (int) Math.round(ROCKET_COOLDOWN_TICKS / enemy.scale()));
        if (enemy.tickRocket(rocketCooldown)) {
            fireRockets(world, enemy, target, salvo);
            sounds.play(SoundFx.BOSS_ROCKET);
        }
    }

    /**
     * How many heavy projectiles this ship launches at a time, or zero if it has no second weapon.
     *
     * A hydra's torso has no mouth of its own -- the heads do the spitting, one ball each -- so it
     * sits this out while its parts carry the secondary between them.
     */
    private static int secondarySalvoFor(EnemyShip enemy) {
        if (enemy.isBossPart()) {
            return 1;
        }
        if (!enemy.isBoss()) {
            return 0;
        }
        return enemy.boss().heads() > 0 ? 0 : ROCKETS_PER_SALVO;
    }

    /**
     * The secondary weapon: a pair of tracking rockets from the flagship's flanks.
     *
     * Slow and heavy on purpose. They turn, but at {@code BOSS_ROCKET_TURN_RATE} a player flying
     * flat out can still lead them into a wide arc and leave, which is the intended answer to them.
     */
    private static void fireRockets(World world, EnemyShip boss, PlayerShip target, int salvo) {
        // A head spits acid; a warship launches ordnance. Both home, both burn out, and both go
        // through Rocket -- which means neither needs a new collision, sweep or render path.
        boolean acid = boss.isBossPart();
        Sprite art = acid ? Sprite.ACID_BALL : Sprite.BOSS_ROCKET;
        double baseSpeed = acid ? GameConfig.ACID_SPEED : GameConfig.BOSS_ROCKET_SPEED;
        int baseDamage = acid ? GameConfig.ACID_DAMAGE : GameConfig.BOSS_ROCKET_DAMAGE;
        double turn = acid ? GameConfig.ACID_TURN_RATE : GameConfig.BOSS_ROCKET_TURN_RATE;
        int fuse = acid ? ACID_FUSE_TICKS : Rocket.FUSE_TICKS;

        double speed = baseSpeed * Math.min(boss.scale(), SPEED_SCALE_CAP);
        int damage = (int) Math.round(baseDamage * boss.scale());
        Orientation facing = boss.orientation();
        double flank = facing.acrossExtent(boss.width(), boss.height()) * 0.35;
        double lead = facing.alongExtent(boss.width(), boss.height()) * 0.2;

        for (int i = 0; i < salvo; i++) {
            // A single-shot salvo comes straight out of the mouth rather than off a flank.
            double side = salvo == 1 ? 0 : (i % 2 == 0 ? -1 : 1);
            double x = boss.centerX() - art.width() / 2 + facing.vx(lead, side * flank);
            double y = boss.centerY() - art.height() / 2 + facing.vy(lead, side * flank);
            world.addBullet(new Rocket(art, x, y, 0, speed, null, target, damage, turn, fuse));
        }
    }

    /** The level is needed only so a spawner's escorts wear the local faction's hull. */
    static void fire(World world, EnemyShip enemy, PlayerShip target, Level level) {
        if (!enemy.isBoss()) {
            // Straight down-arena, no spread.
            addBullet(world, enemy, GameConfig.ENEMY_BULLET_SPEED, 0, GameConfig.ENEMY_BULLET_DAMAGE);
            return;
        }
        firePattern(world, enemy, target, level);
    }

    /** Cooldown for this enemy's next shot: the boss's varies by phase, everything else is fixed. */
    static int cooldownFor(EnemyShip enemy, int difficultyCooldown) {
        if (!enemy.isBoss()) {
            return difficultyCooldown;
        }
        BossPhase phase = enemy.phase();
        double share = enemy.isBossPart() ? PART_COOLDOWN_FACTOR : 1;
        int scaled = (int) Math.round(phase.cooldownTicks() * share / enemy.scale());
        return Math.max(MIN_BOSS_COOLDOWN_TICKS, scaled);
    }

    private static void firePattern(World world, EnemyShip boss, PlayerShip target, Level level) {
        BossPhase phase = boss.phase();
        if (phase == BossPhase.SPAWNER) {
            spawnMinions(world, boss, level);
            return;
        }
        double centreAngle = centreAngleFor(phase, world, boss, target);
        int shots = phase.shots();
        // Distribute the shots evenly either side of the pattern's centre.
        double firstOffset = -phase.spreadRadians() * (shots - 1) / 2.0;

        // Damage scales without limit, speed does not: a bullet faster than the player's own
        // (GameConfig.BULLET_SPEED, 10) stops being dodgeable and starts being unfair.
        double speed = GameConfig.ENEMY_BULLET_SPEED * Math.min(boss.scale(), SPEED_SCALE_CAP);
        int damage = (int) Math.round(GameConfig.ENEMY_BULLET_DAMAGE * boss.scale());
        Orientation facing = boss.orientation();

        for (int i = 0; i < shots; i++) {
            double angle = centreAngle + firstOffset + i * phase.spreadRadians();
            // Zero points down-arena, whichever way that is; the pattern turns with the level.
            addBullet(world, boss, Math.cos(angle) * speed, Math.sin(angle) * speed, damage);
        }
    }

    /** Zero points straight down; positive rotates toward increasing x. */
    private static double centreAngleFor(BossPhase phase, World world, EnemyShip boss,
                                         PlayerShip target) {
        if (phase.aimsAtPlayer() && target != null) {
            Orientation facing = boss.orientation();
            double dx = target.centerX() - boss.centerX();
            double dy = target.centerY() - boss.centerY();
            // Clamped to at least one unit down-arena, so a player who slips behind the flagship
            // cannot make it fire backwards through its own hull. It is the meaning that is
            // generalised here, not just the coordinate: "below me" becomes "ahead of me".
            double along = Math.max(1, facing.along(dx, dy));
            double across = facing.across(dx, dy);
            return Math.atan2(across, along);
        }
        if (phase == BossPhase.SPIRAL) {
            // Unbounded rotation rather than an oscillation, so the stream paints a continuous arc.
            return world.tick() * 0.11;
        }
        if (phase.sweeps()) {
            // A slow oscillation driven by the world clock, so the fan tracks back and forth.
            double sweep = Math.sin(world.tick() / 42.0);
            return sweep * 0.6;
        }
        return 0;
    }

    /**
     * Calls in escorts from the boss's flanks.
     *
     * Note this mutates the enemy list, which is the list {@link GameLoop} is iterating when it calls
     * in here -- that loop indexes rather than using an iterator for exactly this reason.
     */
    private static void spawnMinions(World world, EnemyShip boss, Level level) {
        if (world.enemies().size() + MINIONS_PER_VOLLEY > MAX_ENEMIES_WITH_MINIONS) {
            return;
        }
        Sprite art = level.enemySprite(EnemyShip.EnemyKind.SCOUT);
        Orientation facing = boss.orientation();
        double flank = facing.acrossExtent(boss.width(), boss.height()) * 0.45;
        for (int i = 0; i < MINIONS_PER_VOLLEY; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double x = boss.centerX() - art.width() / 2 + facing.vx(0, side * flank);
            double y = boss.centerY() - art.height() / 2 + facing.vy(0, side * flank);
            EnemyShip minion = new EnemyShip(EnemyShip.EnemyKind.SCOUT, art, x, y);
            world.addEnemy(minion);
        }
    }

    /**
     * Fires one shot from the ship's muzzle.
     *
     * Velocity arrives in arena terms -- {@code along} down-arena, {@code across} sideways -- and
     * is turned into screen coordinates here, so no caller has to know which way the level runs.
     * The muzzle sits three quarters of the way down the hull along the direction of travel.
     */
    private static void addBullet(World world, EnemyShip enemy, double along, double across,
                                  int damage) {
        Orientation facing = enemy.orientation();
        double lead = facing.alongExtent(enemy.width(), enemy.height()) * 0.25;
        double x = enemy.centerX() - Sprite.ENEMY_BULLET.width() / 2 + facing.vx(lead, 0);
        double y = enemy.centerY() - Sprite.ENEMY_BULLET.height() / 2 + facing.vy(lead, 0);
        Bullet bullet = new Bullet(Sprite.ENEMY_BULLET, x, y,
                facing.vx(along, across), facing.vy(along, across), null, damage);
        world.addBullet(bullet);
    }
}
