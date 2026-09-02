package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Ordnance;
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
     * Headroom a spawner is allowed above the preset's own ceiling on enemies.
     *
     * The difficulty cap is enforced in {@link SpawnDirector}, which knows nothing about bosses
     * calling in help, so without a limit here a spawner fills the arena unopposed.
     *
     * Relative rather than the flat twelve it used to be. That flat number was the NORMAL cap
     * plus this, back when that cap was six, which is where the figure came from. It had to stop
     * being flat once a preset could allow twenty ordinary enemies on the field: at a fixed
     * twelve the SPAWNER phase would silently do nothing for the whole of a fight on the two
     * hardest presets, so the flagship would stand there venting no escorts and firing no shots
     * either.
     */
    private static final int MINION_HEADROOM = 6;

    /** Floor on a scaled boss cooldown, so a late loop cannot turn a pattern into a solid wall. */
    private static final int MIN_BOSS_COOLDOWN_TICKS = 4;

    /**
     * What an enraged flagship's reload is multiplied by. Below one is faster.
     *
     * Gentler than the strafe, deliberately. Movement is the half a player answers by moving, and
     * fire rate is the half they cannot; doubling both is how a last phase stops being a fight.
     * MIN_BOSS_COOLDOWN_TICKS still floors the result, so a late loop cannot compound this into a
     * wall.
     */
    private static final double ENRAGE_COOLDOWN_SCALE = 0.80;

    /**
     * The same floor for an ordinary hull, once its archetype's rate is applied to the preset's gap.
     *
     * Higher than the boss floor because there are up to twenty of these on the field at once,
     * against one flagship: a cruiser at DIE's sixteen-tick gap already fires every eighth tick, and
     * that is the point at which a lane of them is a wall rather than a fight.
     */
    private static final int MIN_ENEMY_COOLDOWN_TICKS = 8;

    /**
     * How far apart an ordinary ship's shots go when its wave asked for more than one.
     *
     * Between AIMED_BURST's 0.14 and SWEEPING_FAN's 0.22, so it sits inside the vocabulary of
     * spreads the flagships already established rather than introducing a new one.
     */
    private static final double ENEMY_SPREAD_RADIANS = 0.18;

    /**
     * Ceiling on one ordinary ship's volley.
     *
     * MIN_ENEMY_COOLDOWN_TICKS throttles the rate, not the count, and the two multiply: twenty
     * ships at the hardest preset's gap firing three apiece is already most of a wall. A wave that
     * wants a heavy gun should pair a high count with a slow fireGap rather than reaching past this.
     */
    private static final int MAX_ENEMY_SHOTS = 3;

    /**
     * How much slower one part fires than a whole flagship would, per part the flagship fields.
     *
     * A phase's cooldown was tuned for a boss with one mouth. Three heads firing it unmodified put
     * two and a half times as many shots in the air as any other fight in the game -- measured at
     * fifty-six against the Sentinel's twenty-two. Slightly less than the part count, so a
     * multi-part flagship is still the heaviest barrage in the run without being a wall.
     *
     * Derived rather than fixed. It was a flat 2.4 while every multi-part boss fielded exactly
     * three parts, which made the head count invisible in the arithmetic; the Frozen Empress
     * fields four, and at a flat 2.4 she would have put a third more fire in the air than any
     * fight in the game with nobody having chosen that. At 0.8 a part the three-part bosses --
     * the hydra and the rig alike -- keep exactly the 2.4 they were tuned at.
     */
    private static final double PART_COOLDOWN_PER_PART = 0.8;

    /**
     * Slower by the part count, so total shot density stays flat as parts are added.
     *
     * Package-private rather than private so FrozenEmpressTest can pin the two values that matter
     * without measuring fire rates through a phase machine that varies by boss.
     */
    static double partCooldownFactor(int parts) {
        return Math.max(1, parts) * PART_COOLDOWN_PER_PART;
    }

    /** Ceiling on how far scaling may speed boss projectiles up. */
    private static final double SPEED_SCALE_CAP = 1.5;

    /** Ticks between rocket salvoes at scale 1, and the floor once scaling shortens it. */
    private static final int ROCKET_COOLDOWN_TICKS = 210;
    private static final int MIN_ROCKET_COOLDOWN_TICKS = 60;

    /**
     * Ticks between one part's acid balls, per part the flagship fields.
     *
     * The same shape as PART_COOLDOWN_PER_PART and for the same reason. What is on screen is a
     * function of the part count and the fuse: at one ball per part per cooldown, with a fuse of
     * ACID_FUSE_TICKS, the arena holds {@code parts * fuse / cooldown} of them. That was a flat
     * 210 while every multi-part flagship fielded three parts -- about three balls up, which is
     * what the fuse was sized for -- and a fourth part at a flat 210 raises it to nearly five.
     *
     * Seventy a part is that same 210 at three parts, so the hydra and the rig are unchanged, and
     * it holds the count flat rather than the rate as parts are added.
     */
    private static final int ROCKET_COOLDOWN_PER_PART = 70;

    private static final int ROCKETS_PER_SALVO = 2;

    /**
     * How long a ball of acid lasts.
     *
     * Shorter than a rocket's fuse, and that is the throttle on how many can be in the air: parts
     * spitting on a staggered timer put roughly three on screen at once, whatever the part count,
     * because rocketCooldownFor scales with it. Lengthen this and the arena fills.
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
                             int difficultyCooldown, int enemyCap, SoundPlayer sounds) {
        // The sweep usually clears the dead before this runs, but not always: a hydra head is
        // killed by its own dying torso from inside World.update, which is earlier in the same
        // frame. Without this it gets a parting shot.
        if (!enemy.isAlive()) {
            return;
        }
        if (enemy.tickWeapon(cooldownFor(enemy, difficultyCooldown))) {
            BossPhase fired = enemy.isBoss() ? enemy.firingPhase() : null;
            fire(world, enemy, target, level, enemyCap);
            // Ordinary enemies stay silent, as they always have; a spawner is venting escorts
            // rather than shooting, so a machine gun over it would be describing the wrong thing.
            if (enemy.isBoss() && fired != BossPhase.SPAWNER) {
                sounds.play(fired != null && fired.isSpecial() ? SoundFx.BOSS_ROCKET
                        : SoundFx.BOSS_GUN);
            }
            if (enemy.isBoss()) {
                // After the shot, never before: firingPhase has to give the cooldown and the fire
                // the same answer within one tick.
                enemy.countVolley();
            }
        }
        int salvo = secondarySalvoFor(enemy);
        if (salvo == 0 || target == null) {
            return;
        }
        int rocketCooldown = Math.max(MIN_ROCKET_COOLDOWN_TICKS,
                (int) Math.round(rocketCooldownFor(enemy) / enemy.scale()));
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
    /**
     * Ticks between this ship's heavy shots before difficulty scaling.
     *
     * Package-private for the same reason partCooldownFactor is: the values are worth pinning
     * directly rather than inferring from a fight.
     */
    static int rocketCooldownFor(EnemyShip enemy) {
        return enemy.isBossPart()
                ? ROCKET_COOLDOWN_PER_PART * Math.max(1, enemy.siblingParts())
                : ROCKET_COOLDOWN_TICKS;
    }

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
    static void fire(World world, EnemyShip enemy, PlayerShip target, Level level, int enemyCap) {
        if (!enemy.isBoss()) {
            fireOrdinary(world, enemy);
            return;
        }
        firePattern(world, enemy, target, level, enemyCap);
    }

    /**
     * An ordinary ship's gun: one shot straight down-arena, or a narrow fan if its wave asked.
     *
     * A fan rather than a {@code BossPhase}. Letting an ordinary hull carry a phase would put four
     * more branches in the file that most wants to stay a readable sequence, and would let a wave
     * row hand a scout SPAWNER or a twelve-shot VORTEX. What a wave actually wants from "more
     * firepower" is more bullets, and this is that.
     *
     * At one shot the arithmetic is exactly the single straight shot this used to be: the offset is
     * -0.0, whose cosine is 1 and whose sine is -0.0, so the bullet leaves on the same vector it
     * always did.
     */
    private static void fireOrdinary(World world, EnemyShip enemy) {
        int shots = Math.max(1, Math.min(MAX_ENEMY_SHOTS, enemy.shots()));
        double first = -ENEMY_SPREAD_RADIANS * (shots - 1) / 2.0;
        for (int i = 0; i < shots; i++) {
            double angle = first + i * ENEMY_SPREAD_RADIANS;
            addBullet(world, enemy,
                    Math.cos(angle) * GameConfig.ENEMY_BULLET_SPEED,
                    Math.sin(angle) * GameConfig.ENEMY_BULLET_SPEED,
                    GameConfig.ENEMY_BULLET_DAMAGE, Sprite.ENEMY_BULLET);
        }
    }

    /** Cooldown for this enemy's next shot: the boss's varies by phase, everything else is fixed. */
    static int cooldownFor(EnemyShip enemy, int difficultyCooldown) {
        if (!enemy.isBoss()) {
            // The preset names the gap; the ship decides what share of it it waits -- its
            // archetype's share, times whatever its wave row asked for.
            return Math.max(MIN_ENEMY_COOLDOWN_TICKS,
                    (int) Math.round(difficultyCooldown * enemy.fireFactor()));
        }
        BossPhase phase = enemy.firingPhase();
        double share = enemy.isBossPart() ? partCooldownFactor(enemy.siblingParts()) : 1;
        // An enraged flagship reloads faster as well as strafing harder. Both come off the same
        // flag, so a fight that speeds up does it in one readable way rather than two.
        double rage = enemy.isEnraged() ? ENRAGE_COOLDOWN_SCALE : 1;
        int scaled = (int) Math.round(phase.cooldownTicks() * share * rage / enemy.scale());
        return Math.max(MIN_BOSS_COOLDOWN_TICKS, scaled);
    }

    private static void firePattern(World world, EnemyShip boss, PlayerShip target, Level level,
                                    int enemyCap) {
        BossPhase phase = boss.firingPhase();
        if (phase == BossPhase.SPAWNER) {
            spawnMinions(world, boss, level, enemyCap);
            return;
        }
        double centreAngle = centreAngleFor(phase, world, boss, target);
        int shots = phase.shots();
        // Asked once and used twice, because a phase whose spread varies has to place its first
        // shot and step between its shots at the same figure. Reading phase.spreadRadians() in both
        // places was correct while every pattern's spread was a constant; VORTEX's is not.
        double spread = phase.spreadRadiansAt(world.tick());
        // Distribute the shots evenly either side of the pattern's centre.
        double firstOffset = -spread * (shots - 1) / 2.0;

        // Damage scales without limit, speed does not: a bullet faster than the player's own
        // (GameConfig.BULLET_SPEED, 10) stops being dodgeable and starts being unfair.
        // The galaxy's round decides how fast and how hard; the pattern decides how many and where.
        Ordnance round = boss.boss().ordnance();
        double speed = GameConfig.ENEMY_BULLET_SPEED * round.speedFactor()
                * Math.min(boss.scale(), SPEED_SCALE_CAP);
        int damage = (int) Math.round(GameConfig.ENEMY_BULLET_DAMAGE * round.damageFactor()
                * phase.damageScale() * boss.scale());

        for (int i = 0; i < shots; i++) {
            double angle = centreAngle + firstOffset + i * spread;
            // Zero points down-arena, whichever way that is; the pattern turns with the level.
            addBullet(world, boss, Math.cos(angle) * speed, Math.sin(angle) * speed, damage,
                    round.art());
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
        if (phase == BossPhase.VORTEX) {
            // Unbounded like the spiral, and deliberately less than half its rate: this is twelve
            // shots rather than four, and a full ring turning at spiral speed is a wall with no
            // readable gap in it.
            return world.tick() * 0.045;
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
    private static void spawnMinions(World world, EnemyShip boss, Level level, int enemyCap) {
        if (world.enemies().size() + MINIONS_PER_VOLLEY > enemyCap + MINION_HEADROOM) {
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
                                  int damage, Sprite art) {
        Orientation facing = enemy.orientation();
        double lead = facing.alongExtent(enemy.width(), enemy.height()) * 0.25;
        double x = enemy.centerX() - art.width() / 2 + facing.vx(lead, 0);
        double y = enemy.centerY() - art.height() / 2 + facing.vy(lead, 0);
        Bullet bullet = new Bullet(art, x, y,
                facing.vx(along, across), facing.vy(along, across), null, damage);
        world.addBullet(bullet);
    }
}
