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
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;

/**
 * Applies damage, scoring and pickups.
 *
 * Nothing here removes an entity from a collection; hits only call {@code kill()} and
 * {@link World#sweep()} clears them afterwards.
 */
public final class CollisionSystem {

    /**
     * Chance a defeated boss part leaves a pickup, in percent.
     *
     * A part has no {@code EnemyKind} of its own -- it wears whichever archetype the rig was built
     * from -- so it cannot read its rate off the hull the way an ordinary enemy does. Above every
     * archetype and well below a flagship's guaranteed haul, which is what a head is worth.
     */
    private static final int BOSS_PART_DROP_PERCENT = 25;
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

    /**
     * Chance a hull gives up the weapon restricted to it, in percent.
     *
     * The same figure for all four so no archetype is the one worth farming. It stacks on top of the
     * per-archetype drop rate rather than replacing it, so a scout -- which drops nothing at all --
     * still cannot produce one.
     */
    private static final int RESTRICTED_CHANCE_PERCENT = 22;

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
     * The mega laser: it burns what is standing in front of it, every tick, and stops there.
     *
     * It used to pierce -- no {@code return} in either loop, so a column of six all took the tick
     * and the beam was drawn nose-to-wall through whatever it met. That read as the beam missing a
     * flagship it was in fact killing. It now ends at the first hull in the lane, and everything
     * behind that hull is shielded by it.
     *
     * The stop is applied to the <em>box</em>, once, in {@link PlayerShip#setBeamReach} -- not
     * separately here and in the renderer. Both read {@code beamBox}, and a beam that burns a lane
     * it is not drawn in is the one bug this weapon can have.
     *
     * ponytail: an AABB against every hazard, same straight scan hitHazards documents as having
     * beaten a quadtree by 3.3x at this game's entity counts. One player's beam is now two passes
     * -- find the stop, then burn -- which is still cheaper than one quadtree build.
     */
    private void resolveBeams(World world) {
        for (PlayerShip player : world.players()) {
            player.setBeamReach(PlayerShip.UNSTOPPED);
            if (player.isOut() || !player.isFiringBeam()) {
                continue;
            }
            aimBeam(player, world);
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

    /**
     * How far past the muzzle the beam bites into the hull it stops at.
     *
     * Not zero, and it is load-bearing. {@link #overlaps} is a strict comparison, so a box whose far
     * edge lands exactly on an entity's near edge does not overlap it -- the beam would stop at the
     * one thing in the game it had failed to hit. This much overshoot also puts the impact bloom on
     * the hull rather than floating in front of it.
     */
    private static final double BEAM_BITE = 14;

    /**
     * The share of the player's hull, centred, that enemy fire can actually hit.
     *
     * ponytail: one factor for the whole sprite, not a per-frame mask. Every player frame is the
     * same 60x64 and the cockpit sits in the middle of all of them, so a mask would be five times
     * the data to say what one number says. If the banked frames ever stop being symmetrical, that
     * is the point to give the sprite its own box.
     */
    private static final double PLAYER_CORE = 0.55;

    /**
     * Shortens this player's beam to the nearest thing standing in it.
     *
     * Scanned against the full-length box, so the reach is measured before it is applied. Asteroids
     * count: a beam that stops at a scout but runs straight through a rock reads as broken
     * collision rather than as a rule.
     */
    private static void aimBeam(PlayerShip player, World world) {
        double[] full = player.beamBox();
        double nearest = PlayerShip.UNSTOPPED;
        for (EnemyShip enemy : world.enemies()) {
            if (enemy.isAlive() && overlaps(full, enemy)) {
                nearest = Math.min(nearest, beamDistance(player.facing(), full, enemy));
            }
        }
        for (Asteroid asteroid : world.asteroids()) {
            if (asteroid.isAlive() && overlaps(full, asteroid)) {
                nearest = Math.min(nearest, beamDistance(player.facing(), full, asteroid));
            }
        }
        if (nearest != PlayerShip.UNSTOPPED) {
            player.setBeamReach(nearest + BEAM_BITE);
        }
    }

    /**
     * Muzzle to an entity's near edge, along the beam. Negative when the entity is already past the
     * muzzle, which {@code setBeamReach} floors at zero.
     *
     * {@code beam} must be the unshortened box: the muzzle is whichever of its ends the ship's nose
     * is at, and on a shortened box the far end is no longer the wall.
     */
    private static double beamDistance(Facing facing, double[] beam, Entity target) {
        return switch (facing) {
            case UP -> beam[1] + beam[3] - (target.y() + target.height());
            case DOWN -> target.y() - beam[1];
            case LEFT -> beam[0] + beam[2] - (target.x() + target.width());
            case RIGHT -> target.x() - beam[0];
        };
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
            if (!enemy.isAlive() || !bullet.intersects(enemy) || !bullet.canStillHit(enemy)) {
                continue;
            }
            PlayerShip shooter = bullet.owner();
            // A piercing round carries on, and remembers what it burned so it cannot burn the same
            // hull sixty times a second on its way through.
            if (bullet.pierces()) {
                bullet.recordPierce(enemy);
            } else {
                bullet.kill();
            }
            shooter.recordHit();
            enemy.takeDamage(bullet.damage());
            if (!enemy.isAlive()) {
                awardKill(world, shooter, enemy);
            }
            if (bullet.pierces()) {
                continue;
            }
            detonate(world, shooter, bullet);
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
            if (bullet.pierces()) {
                continue;
            }
            detonate(world, shooter, bullet);
            return;
        }
    }

    /**
     * A shell's blast: everything inside the radius takes it, once.
     *
     * <strong>One explosion, sized off the blast rather than one per victim.</strong>
     * {@code World.addExplosion} kicks the camera on every call, and a nova into a group of eight
     * would otherwise shake the screen eight times in a tick -- the same trap
     * {@code World.killWhatLeftTheArena} documents for rocks grinding into a wall.
     *
     * Measured centre to centre. A radius is not a hitbox, and asking a ring of AABBs which corners
     * are inside a circle is a great deal of arithmetic for a difference nobody can see.
     */
    private void detonate(World world, PlayerShip shooter, Bullet shell) {
        double radius = shell.blastRadius();
        if (radius <= 0) {
            return;
        }
        world.addBlast(shell.centerX(), shell.centerY(), radius);
        int damage = shooter.damageFor(GameConfig.NOVA_BLAST_DAMAGE);
        for (EnemyShip enemy : world.enemies()) {
            if (!enemy.isAlive() || !within(shell, enemy, radius)) {
                continue;
            }
            enemy.takeDamage(damage);
            if (!enemy.isAlive()) {
                // Through awardKill so a blast kill scores, drops and explodes like any other. The
                // extra kills move this class's random stream, which is why BossAndDropsTest reads
                // its rates as bounds rather than as golden numbers.
                awardKill(world, shooter, enemy);
            }
        }
        for (Asteroid asteroid : world.asteroids()) {
            if (!asteroid.isAlive() || !within(shell, asteroid, radius)) {
                continue;
            }
            asteroid.takeDamage(damage);
            if (!asteroid.isAlive()) {
                shooter.addScore(asteroid.scoreValue());
                shooter.recordAsteroidKill();
            }
        }
        sounds.play(SoundFx.EXPLOSION);
    }

    private static boolean within(Entity blast, Entity target, double radius) {
        double dx = target.centerX() - blast.centerX();
        double dy = target.centerY() - blast.centerY();
        return dx * dx + dy * dy <= radius * radius;
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
        int drops = flagship ? BOSS_DROPS : rollOrdinaryDrop(enemy);
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
        // One draw whatever the hull is, taken before the archetype is consulted. Rolling only on
        // the paths that can win would move this class's stream and re-roll every seeded assertion
        // in BossAndDropsTest -- the same reasoning as rollOrdinaryDrop's.
        int roll = random.nextInt(100);
        boolean heavy = enemy.isBoss() || enemy.kind() == EnemyShip.EnemyKind.CRUISER;
        // The beam first and rarest, and still only off something heavy. That restriction is the
        // one drop rule in the game worth being careful with: a scout handing out mega lasers is the
        // single change that would trivialise everything, and lightEnemiesNeverDropTheBeam pins it.
        if (heavy && roll < MEGA_LASER_CHANCE_PERCENT) {
            return PowerUp.Kind.MEGA_LASER;
        }
        PowerUp.Kind restricted = restrictedFor(enemy);
        if (restricted != null && roll < MEGA_LASER_CHANCE_PERCENT + RESTRICTED_CHANCE_PERCENT) {
            return restricted;
        }
        return PowerUp.Kind.randomCommon(random);
    }

    /**
     * The one new weapon this hull can give up, or null for a hull that carries none.
     *
     * Each is restricted to the archetype it belongs to, which is what makes choosing what to fight
     * worth doing: a flagship is the only nova in the game, flak comes off the gunships and the
     * scythe off the fighters. Scouts carry nothing -- they are most of what is on the field and
     * they are chaff.
     */
    private static PowerUp.Kind restrictedFor(EnemyShip enemy) {
        if (enemy.isBoss()) {
            return PowerUp.Kind.NOVA;
        }
        return switch (enemy.kind()) {
            case CRUISER -> PowerUp.Kind.FLAK;
            case FIGHTER -> PowerUp.Kind.SCYTHE;
            case SCOUT -> null;
        };
    }

    /**
     * Whether this hull leaves anything, at the rate its own archetype carries.
     *
     * The draw is taken before the rate is consulted, never after a short-circuit on a zero rate.
     * A scout returning early would move this class's draw stream and re-roll every seeded
     * assertion in {@code BossAndDropsTest} -- for a saving of one {@code nextInt} on the one kill
     * that can never drop anything.
     */
    private int rollOrdinaryDrop(EnemyShip enemy) {
        int roll = random.nextInt(100);
        int chance = enemy.isBossPart() ? BOSS_PART_DROP_PERCENT : enemy.kind().dropChancePercent();
        int drops = roll < chance ? 1 : 0;
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

    /**
     * Incoming fire against the player, tested against the hull's core rather than its whole box.
     *
     * The sprite is 60x64 and most of that is wing. Against a 16x22 bullet the full box gave a
     * kill footprint of 76x86 -- near an eighth of the arena's width for one shot -- so gaps that
     * looked flyable were not, and a clipped wingtip read as an unfair hit. Shrinking the target
     * is the standard answer for the genre and it is the only lever here that widens every gap at
     * once, whatever fired the bullet.
     *
     * Fire only. Ramming and terrain keep the full box: contact already has its own forgiveness in
     * {@link #resolveContact}, and a player who could fly a core through a hull would be passing
     * through ships.
     */
    private void hitPlayers(World world, Bullet bullet) {
        for (PlayerShip player : world.players()) {
            if (player.isOut() || !bullet.intersectsCore(player, PLAYER_CORE)) {
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
