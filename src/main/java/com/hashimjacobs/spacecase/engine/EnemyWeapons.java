package com.hashimjacobs.spacecase.engine;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.BossPhase;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;

/**
 * Enemy firing, kept out of {@link GameLoop} so the loop stays a readable sequence of phases.
 *
 * Ordinary enemies fire one shot straight down. The boss fires a pattern chosen by
 * {@link BossPhase}, which shifts as its health falls.
 */
final class EnemyWeapons {

    private EnemyWeapons() {
    }

    static void fire(World world, EnemyShip enemy, PlayerShip target) {
        if (!enemy.isBoss()) {
            addBullet(world, enemy, 0, GameConfig.ENEMY_BULLET_SPEED, GameConfig.ENEMY_BULLET_DAMAGE);
            return;
        }
        firePattern(world, enemy, target);
    }

    /** Cooldown for this enemy's next shot: the boss's varies by phase, everything else is fixed. */
    static int cooldownFor(EnemyShip enemy, int difficultyCooldown) {
        if (!enemy.isBoss()) {
            return difficultyCooldown;
        }
        BossPhase phase = BossPhase.forHealthFraction(enemy.remainingHealthFraction());
        return phase.cooldownTicks();
    }

    private static void firePattern(World world, EnemyShip boss, PlayerShip target) {
        BossPhase phase = BossPhase.forHealthFraction(boss.remainingHealthFraction());
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
        if (phase.sweeps()) {
            // A slow oscillation driven by the world clock, so the fan tracks back and forth.
            double sweep = Math.sin(world.tick() / 42.0);
            return sweep * 0.6;
        }
        return 0;
    }

    private static void addBullet(World world, EnemyShip enemy, double vx, double vy, int damage) {
        double x = enemy.centerX() - Sprite.ENEMY_BULLET.width() / 2;
        double y = enemy.y() + enemy.height() * 0.75;
        Bullet bullet = new Bullet(Sprite.ENEMY_BULLET, x, y, vx, vy, null, damage);
        world.addBullet(bullet);
    }
}
