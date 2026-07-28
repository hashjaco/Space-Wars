package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Rectangle2D;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;

/**
 * Applies damage, scoring and pickups.
 *
 * Nothing here removes an entity from a collection; hits only call {@code kill()} and
 * {@link World#sweep()} clears them afterwards.
 */
public final class CollisionSystem {

    private static final Rectangle2D ARENA =
            new Rectangle2D(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

    private final QuadTree hazardTree = new QuadTree(ARENA);
    private final List<Entity> candidates = new ArrayList<>();
    private final SoundPlayer sounds;

    public CollisionSystem(SoundPlayer sounds) {
        this.sounds = sounds;
    }

    public void resolve(World world) {
        indexHazards(world);
        resolveBullets(world);
        resolveContact(world);
        resolvePickups(world);
    }

    private void indexHazards(World world) {
        hazardTree.clear();
        for (EnemyShip enemy : world.enemies()) {
            hazardTree.insert(enemy);
        }
        for (Asteroid asteroid : world.asteroids()) {
            hazardTree.insert(asteroid);
        }
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

    private void hitHazards(World world, Bullet bullet) {
        candidates.clear();
        hazardTree.retrieve(candidates, bullet);

        for (Entity target : candidates) {
            if (!target.isAlive() || !bullet.intersects(target)) {
                continue;
            }
            PlayerShip shooter = bullet.owner();
            bullet.kill();

            if (target instanceof EnemyShip enemy) {
                enemy.takeDamage(bullet.damage());
                if (!enemy.isAlive()) {
                    awardKill(world, shooter, enemy);
                }
            } else if (target instanceof Asteroid asteroid) {
                asteroid.takeDamage(bullet.damage());
                if (!asteroid.isAlive()) {
                    shooter.addScore(asteroid.scoreValue());
                    shooter.recordAsteroidKill();
                    world.addExplosion(asteroid, Explosion.SMALL);
                    sounds.play(SoundFx.EXPLOSION);
                }
            }
            return;
        }
    }

    private void awardKill(World world, PlayerShip shooter, EnemyShip enemy) {
        shooter.addScore(enemy.scoreValue());
        shooter.recordEnemyKill();
        world.addExplosion(enemy, Explosion.LARGE);
        sounds.play(SoundFx.EXPLOSION);
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

    private void resolveContact(World world) {
        for (PlayerShip player : world.players()) {
            if (player.isOut()) {
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
