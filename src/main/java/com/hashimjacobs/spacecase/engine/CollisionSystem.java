package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    /** Chance a defeated ordinary enemy leaves a pickup behind, in percent. */
    private static final int DROP_CHANCE_PERCENT = 28;
    /** How many pickups a defeated boss leaves. */
    private static final int BOSS_DROPS = 3;

    private final QuadTree hazardTree = new QuadTree(ARENA);
    private final List<Entity> candidates = new ArrayList<>();
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
            shooter.recordHit();

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
        int drops = enemy.isBoss() ? BOSS_DROPS : rollOrdinaryDrop();
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
