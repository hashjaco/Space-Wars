package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
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

    private EnemyWeapons() {
    }

    /** The level is needed only so a spawner's escorts wear the local faction's hull. */
    static void fire(World world, EnemyShip enemy, PlayerShip target, Level level) {
        if (!enemy.isBoss()) {
            addBullet(world, enemy, 0, GameConfig.ENEMY_BULLET_SPEED, GameConfig.ENEMY_BULLET_DAMAGE);
            return;
        }
        firePattern(world, enemy, target, level);
    }

    /** Cooldown for this enemy's next shot: the boss's varies by phase, everything else is fixed. */
    static int cooldownFor(EnemyShip enemy, int difficultyCooldown) {
        if (!enemy.isBoss()) {
            return difficultyCooldown;
        }
        BossPhase phase = enemy.boss().phaseFor(enemy.remainingHealthFraction());
        return phase.cooldownTicks();
    }

    private static void firePattern(World world, EnemyShip boss, PlayerShip target, Level level) {
        BossPhase phase = boss.boss().phaseFor(boss.remainingHealthFraction());
        if (phase == BossPhase.SPAWNER) {
            spawnMinions(world, boss, level);
            return;
        }
        double centreAngle = centreAngleFor(phase, world, boss, target);
        int shots = phase.shots();
        // Distribute the shots evenly either side of the pattern's centre.
        double firstOffset = -phase.spreadRadians() * (shots - 1) / 2.0;

        for (int i = 0; i < shots; i++) {
            double angle = centreAngle + firstOffset + i * phase.spreadRadians();
            double vx = Math.sin(angle) * GameConfig.ENEMY_BULLET_SPEED;
            double vy = Math.cos(angle) * GameConfig.ENEMY_BULLET_SPEED;
            addBullet(world, boss, vx, vy, GameConfig.ENEMY_BULLET_DAMAGE);
        }
    }

    /** Zero points straight down; positive rotates toward increasing x. */
    private static double centreAngleFor(BossPhase phase, World world, EnemyShip boss,
                                         PlayerShip target) {
        if (phase.aimsAtPlayer() && target != null) {
            double dx = target.centerX() - boss.centerX();
            double dy = Math.max(1, target.centerY() - boss.centerY());
            return Math.atan2(dx, dy);
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
        for (int i = 0; i < MINIONS_PER_VOLLEY; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double x = boss.centerX() + side * boss.width() * 0.45 - art.width() / 2;
            double y = boss.y() + boss.height() * 0.5;
            EnemyShip minion = new EnemyShip(EnemyShip.EnemyKind.SCOUT, art, x, y);
            world.addEnemy(minion);
        }
    }

    private static void addBullet(World world, EnemyShip enemy, double vx, double vy, int damage) {
        double x = enemy.centerX() - Sprite.ENEMY_BULLET.width() / 2;
        double y = enemy.y() + enemy.height() * 0.75;
        Bullet bullet = new Bullet(Sprite.ENEMY_BULLET, x, y, vx, vy, null, damage);
        world.addBullet(bullet);
    }
}
