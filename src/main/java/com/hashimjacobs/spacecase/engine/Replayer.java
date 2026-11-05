package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.asset.SoundPlayer;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.net.LevelStart;
import com.hashimjacobs.spacecase.net.Replay;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * Plays a {@link Replay} back without a display and reports the scores it reaches.
 *
 * A mirror of the parts of {@link GameLoop} that move the simulation -- the fight tick in the order
 * controllers, update, enemies, director, collisions, sweep; the debrief bonus when a level clears;
 * the terms of the next level -- and nothing else. The victory lap, debrief screen, garage and warp
 * are local phases that only touch the clock, and the recorded {@link LevelStart} says where the
 * clock was when the next fight began. {@code DeterminismTest} pins the tick order; {@code
 * ReplayTest} pins that a recorded run replays to its own score.
 *
 * ponytail: a copy of GameLoop's order rather than a shared method. GameLoop needs a Scene, a
 * SoundBank and four preference stores; extracting the fight into something both could call means
 * threading those through here, which is the JavaFX dependency this class exists to not have.
 */
public final class Replayer {

    private static final PlayerControls NO_KEYS = new PlayerControls(
            Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A), Set.of(KeyCode.D), Set.of(KeyCode.SPACE));

    public record Outcome(List<Integer> scores, int ticks, boolean roundOver) {
    }

    private Replayer() {
    }

    /** Drives every enemy the way GameLoop does: track, then fire if it has entered the arena. */
    static void driveEnemies(World world, SpawnDirector director, Difficulty difficulty, SoundPlayer sounds) {
        int difficultyCooldown = difficulty.enemyFireCooldown();
        int enemyCap = difficulty.maxEnemies(world.players().size());
        List<EnemyShip> enemies = world.enemies();
        for (int i = 0, count = enemies.size(); i < count; i++) {
            EnemyShip enemy = enemies.get(i);
            PlayerShip target = world.nearestPlayer(enemy);
            if (target == null) {
                continue;
            }
            enemy.trackAcross(target);
            if (!enemy.hasEntered()) {
                continue;
            }
            EnemyWeapons.driveWeapons(world, enemy, target, director.level(), difficultyCooldown, enemyCap, sounds);
        }
    }

    public static Outcome run(Replay.Parsed replay) {
        Replay.Header header = replay.header();
        if (header.resumed()) {
            throw new IllegalArgumentException("a run resumed from a checkpoint cannot be replayed from its seed");
        }
        Random random = new Random(header.seed());
        World world = new World(header.mode());
        Difficulty difficulty = header.difficulty();
        SpawnDirector director = new SpawnDirector(random, difficulty, header.mode().rules());
        CollisionSystem collisions = new CollisionSystem(SoundPlayer.SILENT, random);
        List<ShipController> controllers = new ArrayList<>();
        Map<Integer, Debrief.Tally> levelStart = new HashMap<>();

        int ticks = 0;
        boolean first = true;
        for (Replay.Segment segment : replay.segments()) {
            LevelStart terms = segment.start();
            if (!first) {
                // The last level cleared: pay its bonuses, then take the next level's terms exactly
                // as stepWarp does once the local phases are over.
                scoreLevel(world, director, levelStart);
                world.reviveFallenAllies();
                world.clearBattlefield();
                world.resumeAt(terms.resumeTick());
                director.advanceLevel();
            }
            world.enterLevel(director.level());
            if (director.level() != terms.slot().level()) {
                throw new IllegalArgumentException("replay expects " + terms.slot().level() + " but the director is on " + director.level());
            }
            for (PlayerShip player : world.players()) {
                player.returnToSpawn();
            }
            world.fitLoadouts(terms.loadouts());
            if (first) {
                for (PlayerShip player : world.players()) {
                    controllers.add(new ShipController(player, NO_KEYS, number -> null));
                }
                first = false;
            }
            snapshot(world, levelStart);

            for (Intent[] intents : segment.ticks()) {
                boolean frozen = world.frozen();
                for (int i = 0; i < controllers.size(); i++) {
                    if (!frozen) {
                        controllers.get(i).apply(intents[i], world, SoundPlayer.SILENT);
                    }
                }
                world.update();
                if (!frozen) {
                    driveEnemies(world, director, difficulty, SoundPlayer.SILENT);
                    director.update(world);
                    collisions.resolve(world);
                }
                world.sweep();
                ticks++;
                if (director.levelCleared() || roundOver(world)) {
                    break;
                }
            }
        }
        List<Integer> scores = world.players().stream().map(PlayerShip::score).toList();
        return new Outcome(scores, ticks, roundOver(world));
    }

    private static boolean roundOver(World world) {
        long stillIn = world.players().stream().filter(player -> !player.isOut()).count();
        return world.rules().lastPlayerStanding() ? stillIn <= 1 : stillIn == 0;
    }

    private static void snapshot(World world, Map<Integer, Debrief.Tally> levelStart) {
        levelStart.clear();
        for (PlayerShip player : world.players()) {
            levelStart.put(player.playerNumber(), tally(player));
        }
    }

    static Debrief.Tally tally(PlayerShip player) {
        return new Debrief.Tally(player.enemiesKilled(), player.asteroidsDestroyed(), player.shotsFired(),
                player.shotsHit(), player.damageTaken(), player.lives());
    }

    /** The debrief bonus, which is simulation state: every peer and this replay pay it alike. */
    static void scoreLevel(World world, SpawnDirector director, Map<Integer, Debrief.Tally> levelStart) {
        Level level = director.level();
        for (PlayerShip player : world.players()) {
            Debrief debrief = Debrief.of(player.name(), level.indexInGalaxy(), level.galaxy().number(),
                    director.parTicks(), levelStart.get(player.playerNumber()), tally(player),
                    director.ticksIntoLevel());
            player.addScore(debrief.totalBonus());
        }
    }
}
