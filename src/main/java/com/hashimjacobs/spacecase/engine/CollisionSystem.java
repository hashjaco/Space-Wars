package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;

/**
 * Applies damage, scoring and pickups.
 *
 * Nothing here removes an entity from a collection; hits only call {@code kill()} and
 * {@link World#sweep()} clears them afterwards.
 */
public final class CollisionSystem {

    /** Chance a defeated ordinary enemy leaves a pickup behind, in percent. */
    private static final int DROP_CHANCE_PERCENT = 28;
    /** How many pickups a defeated boss leaves. */
    private static final int BOSS_DROPS = 3;

    private final SoundPlayer sounds;
    private final Random random;

    public CollisionSystem(SoundPlayer sounds) {
        this(sounds, new Random());
    }

    public CollisionSystem(SoundPlayer sounds, Random random) {
        this.sounds = sounds;
        this.random = random;
    }

    public void resolve(World world) {
        resolveBullets(world);
        resolveContact(world);
        resolvePickups(world);
    }

    private void resolveBullets(World world) {
        boolean friendlyFire = world.rules().friendlyFire();

        for (Bullet bullet : world.bullets()) {
            if (!bullet.isAlive()) {
                continue;
            }

            if (bullet.firedByPlayer()) {
                hitHazards(world, bullet);
                if (friendlyFire && bullet.isAlive()) {
                    hitOpposingPlayer(world, bullet);
                }
                continue;
            }

            hitPlayers(world, bullet);
        }
    }

    /**
     * A straight scan over everything shootable, enemies first.
     *
     * There used to be a quadtree here. It was measured against this: at the game's real load --
     * a dozen enemies, ten asteroids, sixty shots in the air -- the tree did a third as many
     * overlap tests and still ran 3.3x slower, because an overlap test is four float comparisons
     * and the tree spent more than that per entity on traversal and on copying candidates into a
     * list. It was still losing at ten times this game's entity counts.
     *
     * ponytail: O(bullets x hazards), which is the right algorithm below a few thousand entities.
     * If some future mode fields that many, sort hazards on one axis and sweep -- everything here
     * moves along a single axis, so a sweep suits the data far better than a tree ever did.
     */
    private void hitHazards(World world, Bullet bullet) {
        // Enemies before asteroids so a shot into an overlapping pair reliably hits the ship,
        // which is the one the player was aiming at and the one that pays.
        for (EnemyShip enemy : world.enemies()) {
            if (!enemy.isAlive() || !bullet.intersects(enemy)) {
                continue;
            }
            PlayerShip shooter = bullet.owner();
            bullet.kill();
            shooter.recordHit();
            enemy.takeDamage(bullet.damage());
            if (!enemy.isAlive()) {
                awardKill(world, shooter, enemy);
            }
            return;
        }

        for (Asteroid asteroid : world.asteroids()) {
            if (!asteroid.isAlive() || !bullet.intersects(asteroid)) {
                continue;
            }
            PlayerShip shooter = bullet.owner();
            bullet.kill();
            shooter.recordHit();
            asteroid.takeDamage(bullet.damage());
            if (!asteroid.isAlive()) {
                shooter.addScore(asteroid.scoreValue());
                shooter.recordAsteroidKill();
                world.addExplosion(asteroid, Explosion.SMALL);
                sounds.play(SoundFx.EXPLOSION);
            }
            return;
        }
    }

    private void awardKill(World world, PlayerShip shooter, EnemyShip enemy) {
        shooter.addScore(enemy.scoreValue());
        shooter.recordEnemyKill();
        world.addExplosion(enemy, Explosion.LARGE);
        // The long sample, which asteroids deliberately do not get: they die several a second.
        sounds.play(SoundFx.SHIP_EXPLOSION);
        dropLoot(world, enemy);
    }

    /**
     * Pickups come from defeated enemies rather than falling out of empty sky, so they read as a
     * reward. Battle mode has no enemies, so {@link SpawnDirector} still drops them ambiently there.
     */
    private void dropLoot(World world, EnemyShip enemy) {
        if (!world.rules().spawnPowerUps()) {
            return;
        }
        // A part rolls the ordinary chance: three heads paying the full flagship haul each would
        // bury the arena in pickups before the real fight started.
        boolean flagship = enemy.isBoss() && !enemy.isBossPart();
        int drops = flagship ? BOSS_DROPS : rollOrdinaryDrop();
        for (int i = 0; i < drops; i++) {
            PowerUp.Kind[] kinds = PowerUp.Kind.values();
            PowerUp.Kind kind = kinds[random.nextInt(kinds.length)];
            // Fan multiple drops out so they do not stack into a single collectable.
            double offset = (i - (drops - 1) / 2.0) * 46;
            double x = enemy.centerX() - kind.sprite().width() / 2 + offset;
            PowerUp powerUp = new PowerUp(kind, x, enemy.centerY());
            world.addPowerUp(powerUp);
        }
    }

    private int rollOrdinaryDrop() {
        int drops = random.nextInt(100) < DROP_CHANCE_PERCENT ? 1 : 0;
        return drops;
    }

    private void hitOpposingPlayer(World world, Bullet bullet) {
        for (PlayerShip player : world.players()) {
            boolean self = player == bullet.owner();
            if (self || player.isOut() || !bullet.intersects(player)) {
                continue;
            }
            bullet.kill();
            boolean lostALife = player.takeDamage(bullet.damage());
            sounds.play(SoundFx.COLLISION);
            if (lostALife) {
                world.addExplosion(player, Explosion.LARGE);
            }
            return;
        }
    }

    private void hitPlayers(World world, Bullet bullet) {
        for (PlayerShip player : world.players()) {
            if (player.isOut() || !bullet.intersects(player)) {
                continue;
            }
            bullet.kill();
            boolean lostALife = player.takeDamage(bullet.damage());
            sounds.play(SoundFx.COLLISION);
            if (lostALife) {
                world.addExplosion(player, Explosion.LARGE);
            }
            return;
        }
    }

    /**
     * Ramming damage, with a moment's grace between hits.
     *
     * The grace is not cosmetic. Contact is tested every frame and a boss is never destroyed by a
     * ram, so overlapping one used to cost a full contact hit sixty times a second -- a player
     * pinned against a flagship died in under a second with no counterplay. Reusing the hit flash
     * as the window means the ship is visibly recovering for exactly as long as it is protected.
     */
    private void resolveContact(World world) {
        for (PlayerShip player : world.players()) {
            if (player.isOut() || player.justHit()) {
                continue;
            }

            for (Asteroid asteroid : world.asteroids()) {
                if (!asteroid.isAlive() || !player.intersects(asteroid)) {
                    continue;
                }
                asteroid.kill();
                player.takeDamage(asteroid.contactDamage());
                world.addExplosion(asteroid, Explosion.SMALL);
                sounds.play(SoundFx.COLLISION);
            }

            for (EnemyShip enemy : world.enemies()) {
                if (!enemy.isAlive() || !player.intersects(enemy)) {
                    continue;
                }
                // A boss shrugs off contact rather than dying to a ram.
                if (!enemy.isBoss()) {
                    enemy.kill();
                    world.addExplosion(enemy, Explosion.LARGE);
                }
                player.takeDamage(GameConfig.ENEMY_CONTACT_DAMAGE);
                sounds.play(SoundFx.COLLISION);
            }
        }
    }

    private void resolvePickups(World world) {
        for (PowerUp powerUp : world.powerUps()) {
            if (!powerUp.isAlive()) {
                continue;
            }
            for (PlayerShip player : world.players()) {
                if (player.isOut() || !powerUp.intersects(player)) {
                    continue;
                }
                powerUp.kill();
                player.collect(powerUp.kind());
                sounds.play(SoundFx.LASER);
                break;
            }
        }
    }
}
