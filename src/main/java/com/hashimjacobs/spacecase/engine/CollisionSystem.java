package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.garage.Upgrade;
import com.hashimjacobs.spacecase.mode.WorldTemplate;
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

    /** Chance a defeated ordinary enemy leaves a pickup behind, in percent. */
    private static final int DROP_CHANCE_PERCENT = 28;
    /** How many pickups a defeated boss leaves. */
    private static final int BOSS_DROPS = 3;

    /**
     * Chance a heavy hull or a flagship gives up its beam, in percent.
     *
     * Below one-in-six, which is what each of the six common pickups is worth once a drop happens,
     * so the beam is rarer than any of them as well as being restricted to the enemies worth
     * killing for it.
     */
    private static final int MEGA_LASER_CHANCE_PERCENT = 12;

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
        resolveBeams(world);
        resolveBullets(world);
        resolveContact(world);
        resolveTerrain(world);
        resolvePickups(world);
    }

    /**
     * Scraping the tunnel wall: pushed out always, hurt on the ram grace period.
     *
     * The two halves are deliberately on different rules. The push has to happen even during the
     * grace window -- skip it and the ship sinks into the rock for eighteen ticks and then pops back
     * out, which looks like the collision is broken rather than like mercy. The damage uses the same
     * grace as ramming an enemy, because being pinned against a wall should cost about what being
     * pinned against a cruiser costs.
     *
     * Ambient damage keeps its own clock rather than reusing that grace. {@code takeDamage} sets the
     * hit flash, and {@code resolveContact} skips a player who was just hit -- so heat ticking on the
     * grace window would leave a pilot in a lava level permanently immune to being rammed.
     */
    private void resolveTerrain(World world) {
        Terrain terrain = world.terrain();
        if (terrain.isEmpty()) {
            return;
        }
        WorldTemplate template = terrain.template();
        for (PlayerShip player : world.players()) {
            if (!player.isAlive()) {
                continue;
            }
            boolean scraped = terrain.pushInside(player);
            if (scraped && !player.justHit() && template.contactDamage() > 0) {
                player.takeDamage(template.contactDamage());
                sounds.play(SoundFx.COLLISION);
            }
            if (template.ambientDamage() > 0 && world.tick() % 60 == 0) {
                player.takeDamage(template.ambientDamage());
            }
        }
    }

    /**
     * The mega laser: it burns everything standing in the lane, every tick, and stops at nothing.
     *
     * Deliberately unlike {@link #hitHazards}, which kills the round and returns on its first
     * contact. A beam has nothing to spend and nowhere to stop, so there is no {@code return} here
     * -- a column of six enemies all take the tick. That, and not the damage figure, is what makes
     * the weapon feel like an incinerator rather than a fast gun.
     *
     * ponytail: an AABB against every hazard, same straight scan hitHazards documents as having
     * beaten a quadtree by 3.3x at this game's entity counts. One player's beam is one pass.
     */
    private void resolveBeams(World world) {
        for (PlayerShip player : world.players()) {
            if (player.isOut() || !player.isFiringBeam()) {
                continue;
            }
            double[] beam = player.beamBox();
            // One shot a tick, not one per thing burned: otherwise standing in a dense wave would
            // report an accuracy of several hundred percent.
            player.recordShot();
            // Firepower scales the beam as it scales everything; the coil is the track bought
            // specifically for it, so the two compound -- which is why both are short ladders.
            int perTick = GameConfig.BEAM_DAMAGE_PER_TICK
                    + player.loadout().level(Upgrade.FOCUS) * GameConfig.UPGRADE_BEAM_STEP;
            int damage = player.damageFor(perTick);

            for (EnemyShip enemy : world.enemies()) {
                if (!enemy.isAlive() || !overlaps(beam, enemy)) {
                    continue;
                }
                player.recordHit();
                enemy.takeDamage(damage);
                if (!enemy.isAlive()) {
                    awardKill(world, player, enemy);
                }
            }

            for (Asteroid asteroid : world.asteroids()) {
                if (!asteroid.isAlive() || !overlaps(beam, asteroid)) {
                    continue;
                }
                player.recordHit();
                asteroid.takeDamage(damage);
                if (!asteroid.isAlive()) {
                    player.addScore(asteroid.scoreValue());
                    player.recordAsteroidKill();
                    world.addExplosion(asteroid, Explosion.SMALL);
                    sounds.play(SoundFx.EXPLOSION);
                }
            }
        }
    }

    /** {@code box} is {@code {x, y, width, height}}, as {@code PlayerShip.beamBox} returns it. */
    private static boolean overlaps(double[] box, Entity entity) {
        boolean hit = box[0] < entity.x() + entity.width()
                && box[0] + box[2] > entity.x()
                && box[1] < entity.y() + entity.height()
                && box[1] + box[3] > entity.y();
        return hit;
    }

    private void resolveBullets(World world) {
        boolean friendlyFire = world.rules().friendlyFire();

        Terrain terrain = world.terrain();
        for (Bullet bullet : world.bullets()) {
            if (!bullet.isAlive()) {
                continue;
            }

            // Rock eats shots from either side. One check rather than a rule per shooter, because
            // "you cannot shoot through a wall" is not a thing that depends on who fired.
            if (terrain.solidAt(bullet.centerX(), bullet.centerY())) {
                bullet.kill();
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
            PowerUp.Kind kind = rollKind(enemy);
            // Fan multiple drops out so they do not stack into a single collectable.
            double offset = (i - (drops - 1) / 2.0) * 46;
            double x = enemy.centerX() - kind.sprite().width() / 2 + offset;
            PowerUp powerUp = new PowerUp(kind, x, enemy.centerY());
            world.addPowerUp(powerUp);
        }
    }

    /**
     * What this enemy leaves behind. Scouts and fighters never carry the beam.
     *
     * Rolled per drop rather than per kill, so a flagship's three-pickup haul gets three separate
     * chances at it -- which is the point of fighting one.
     */
    private PowerUp.Kind rollKind(EnemyShip enemy) {
        boolean heavy = enemy.isBoss() || enemy.kind() == EnemyShip.EnemyKind.CRUISER;
        if (heavy && random.nextInt(100) < MEGA_LASER_CHANCE_PERCENT) {
            return PowerUp.Kind.MEGA_LASER;
        }
        return PowerUp.Kind.randomCommon(random);
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
