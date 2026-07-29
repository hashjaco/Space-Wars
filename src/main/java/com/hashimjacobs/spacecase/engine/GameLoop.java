package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import javafx.animation.AnimationTimer;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Rank;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.prefs.Standing;

/**
 * The per-frame sequence: read input, simulate, resolve collisions, remove the dead, draw.
 *
 * Removal is deliberately a separate step that runs after collision handling, never during it.
 *
 * Clearing a level does not drop straight into the next one. The loop runs a short {@link Phase}
 * sequence instead -- fly off the top, read the debrief, then warp -- which is what turns eight levels
 * into eight places rather than one endless wave stream.
 */
public final class GameLoop {

    /** Where the loop is between levels. Everything except FIGHTING is the celebration. */
    private enum Phase { FIGHTING, VICTORY_LAP, DEBRIEF, WARP }

    private static final int VICTORY_LAP_TICKS = 96;
    private static final double VICTORY_LAP_SPEED = 2.5;
    private static final double VICTORY_LAP_ACCELERATION = 0.34;

    /**
     * Shortest the warp can last, at 60 steps a second.
     *
     * A floor rather than a duration: the warp also waits on the next level's art finishing its
     * decode, and this keeps the fade watchable when that comes back immediately.
     */
    private static final int WARP_MINIMUM_TICKS = 78;

    private final World world;
    private final SpawnDirector director;
    private final CollisionSystem collisions;
    private final Renderer renderer;
    private final InputState input;
    private final SoundBank sounds;
    private final Settings settings;
    private final Pilots pilots;
    private final List<ShipController> controllers = new ArrayList<>();
    private final Consumer<RoundResult> onRoundOver;

    private final FixedTimestep timestep = new FixedTimestep();
    private final Map<Integer, Debrief.Tally> levelStart = new HashMap<>();
    private final List<Debrief> debriefs = new ArrayList<>();
    private final List<Standing> standings = new ArrayList<>();

    private AnimationTimer timer;
    private Phase phase = Phase.FIGHTING;
    private int phaseTicks;
    private boolean debriefArmed;
    private boolean paused;
    private boolean finished;
    private boolean bossMusicPlaying;

    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Pilots pilots, Random random,
                    Consumer<RoundResult> onRoundOver) {
        this.world = new World(mode, List.of(pilots.name(1), pilots.name(2)));
        this.renderer = renderer;
        this.input = input;
        this.sounds = sounds;
        this.settings = settings;
        this.pilots = pilots;
        this.onRoundOver = onRoundOver;
        this.director = new SpawnDirector(random, settings.difficulty(), mode.rules());
        this.collisions = new CollisionSystem(sounds, random);
        attachControllers();
        snapshotLevelStart();
    }

    private void attachControllers() {
        List<PlayerShip> players = world.players();
        boolean solo = players.size() == 1;
        for (PlayerShip player : players) {
            PlayerControls controls = player.playerNumber() == 1
                    ? PlayerControls.playerOne(solo)
                    : PlayerControls.playerTwo();
            controllers.add(new ShipController(player, controls));
        }
    }

    public void start() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                frame(now);
            }
        };
        timer.start();
    }

    /**
     * Advances the simulation by however many fixed steps the elapsed time earned, then draws once.
     * Rendering every frame while stepping at a fixed rate is what keeps the game the same speed on
     * a 60Hz and a 120Hz display.
     */
    private void frame(long frameNanos) {
        if (paused || finished) {
            return;
        }
        int steps = timestep.stepsFor(frameNanos);
        for (int i = 0; i < steps && !finished; i++) {
            step();
        }
        renderer.draw(world, director);
        switch (phase) {
            case DEBRIEF -> renderer.drawDebrief(director.level(), debriefs, standings, debriefArmed);
            case WARP -> renderer.drawWarpVeil(warpOpacity());
            default -> {
            }
        }
    }

    /**
     * Fade to black and back, peaking halfway through the minimum warp.
     *
     * Holds full black past the halfway point when the next level's art is still decoding, so a slow
     * fault-in shows as a longer blackout rather than as a stutter in a half-faded scene.
     */
    private double warpOpacity() {
        double half = WARP_MINIMUM_TICKS / 2.0;
        if (phaseTicks < half) {
            return phaseTicks / half;
        }
        if (!Assets.warmedUp()) {
            return 1;
        }
        double out = (phaseTicks - half) / half;
        double opacity = Math.max(0, 1 - out);
        return opacity;
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    /** One simulation step. Deliberately does no drawing -- {@link #frame} owns that. */
    private void step() {
        switch (phase) {
            case FIGHTING -> stepFight();
            case VICTORY_LAP -> stepVictoryLap();
            case DEBRIEF -> stepDebrief();
            case WARP -> stepWarp();
        }
    }

    private void stepFight() {
        for (ShipController controller : controllers) {
            controller.apply(input, world, sounds);
        }

        world.update();
        driveEnemies();
        director.update(world);
        collisions.resolve(world);
        world.sweep();

        updateBossMusic();
        if (director.levelCleared()) {
            beginVictoryLap();
            return;
        }
        checkRoundOver();
    }

    /**
     * The flagship is dead: score the level, clear the sky, and let the players run for the exit.
     *
     * Scoring happens here rather than on the debrief screen so the numbers are fixed at the moment
     * the fight ended, before the players are moved anywhere.
     */
    private void beginVictoryLap() {
        phase = Phase.VICTORY_LAP;
        phaseTicks = 0;
        scoreLevel();
        world.clearBattlefield();
        sounds.play(SoundFx.LEVEL_CLEAR);
    }

    /**
     * Players accelerate off the top of the screen.
     *
     * Runs {@link World#tickScenery()} rather than {@code update()} on purpose: a full update expires
     * anything outside the arena, which would delete the ships the moment they left it.
     */
    private void stepVictoryLap() {
        phaseTicks++;
        world.tickScenery();

        double step = VICTORY_LAP_SPEED + phaseTicks * VICTORY_LAP_ACCELERATION;
        for (PlayerShip player : world.players()) {
            if (player.isOut()) {
                continue;
            }
            player.setPosition(player.x(), player.y() + player.facing().yDirection() * step);
        }
        if (phaseTicks >= VICTORY_LAP_TICKS) {
            phase = Phase.DEBRIEF;
            phaseTicks = 0;
            debriefArmed = false;
            input.clear();
        }
    }

    /**
     * Holds on the stats until a button is pressed.
     *
     * Arms only once every key is up, so the shot that killed the boss cannot also skip the screen it
     * earned.
     */
    private void stepDebrief() {
        phaseTicks++;
        world.tickScenery();

        if (!debriefArmed) {
            debriefArmed = !input.anyHeld();
            return;
        }
        if (!input.anyHeld()) {
            return;
        }
        phase = Phase.WARP;
        phaseTicks = 0;
        input.clear();
        Level next = director.level().next();
        Assets.preload(next.layers(), next.boss().art());
    }

    /** Blackout while the next level's art decodes, then hand the ships back to the players. */
    private void stepWarp() {
        phaseTicks++;
        world.tickScenery();
        if (phaseTicks < WARP_MINIMUM_TICKS || !Assets.warmedUp()) {
            return;
        }
        director.advanceLevel();
        for (PlayerShip player : world.players()) {
            player.returnToSpawn();
        }
        snapshotLevelStart();
        sounds.playMusic(world.mode().music());
        bossMusicPlaying = false;
        phase = Phase.FIGHTING;
        phaseTicks = 0;
        timestep.reset();
    }

    /** Records where each player's counters stood as a level began, for the debrief to subtract. */
    private void snapshotLevelStart() {
        levelStart.clear();
        for (PlayerShip player : world.players()) {
            levelStart.put(player.playerNumber(), tally(player));
        }
    }

    private static Debrief.Tally tally(PlayerShip player) {
        Debrief.Tally snapshot = new Debrief.Tally(player.enemiesKilled(),
                player.asteroidsDestroyed(), player.shotsFired(), player.shotsHit(),
                player.damageTaken(), player.lives());
        return snapshot;
    }

    /** Pays every surviving player their bonuses and credits their career, recording promotions. */
    private void scoreLevel() {
        debriefs.clear();
        standings.clear();

        for (PlayerShip player : world.players()) {
            Debrief.Tally before = levelStart.get(player.playerNumber());
            Debrief debrief = Debrief.of(player.name(), director.level().number(),
                    director.parTicks(), before, tally(player), director.ticksIntoLevel());
            debriefs.add(debrief);

            Rank held = pilots.rank(player.name());
            player.addScore(debrief.totalBonus());
            int career = pilots.addCareerScore(player.name(), debrief.totalBonus());
            Rank earned = Rank.forCareerScore(career);
            standings.add(new Standing(player.name(), earned, career, earned != held));
        }
    }

    /** Swaps to the boss track while a boss is on screen, and back to the mode's track after. */
    private void updateBossMusic() {
        boolean bossOnScreen = world.bossPresent();
        if (bossOnScreen == bossMusicPlaying) {
            return;
        }
        bossMusicPlaying = bossOnScreen;
        MusicCue track = bossOnScreen ? MusicCue.BOSS : world.mode().music();
        sounds.playMusic(track);
    }

    /**
     * Indexes rather than iterating: a SPAWNER-phase boss adds enemies from inside
     * {@link EnemyWeapons#fire}, which an iterator over the same list would answer with a
     * ConcurrentModificationException. Bounding the loop up front also means escorts are not driven
     * on the tick they arrive.
     */
    private void driveEnemies() {
        int difficultyCooldown = settings.difficulty().enemyFireCooldown();
        List<EnemyShip> enemies = world.enemies();
        for (int i = 0, count = enemies.size(); i < count; i++) {
            EnemyShip enemy = enemies.get(i);
            PlayerShip target = world.nearestPlayer(enemy);
            if (target == null) {
                continue;
            }
            enemy.trackHorizontally(target);
            boolean onScreen = enemy.y() > -enemy.height() / 2;
            int cooldown = EnemyWeapons.cooldownFor(enemy, difficultyCooldown);
            if (!onScreen || !enemy.tickWeapon(cooldown)) {
                continue;
            }
            EnemyWeapons.fire(world, enemy, target, director.level());
        }
    }

    private void checkRoundOver() {
        List<PlayerShip> players = world.players();
        long stillIn = players.stream().filter(player -> !player.isOut()).count();

        boolean over = world.rules().lastPlayerStanding()
                ? stillIn <= 1
                : stillIn == 0;
        if (!over) {
            return;
        }
        finished = true;
        stop();
        RoundResult result = RoundResult.of(world, director);
        onRoundOver.accept(result);
    }

    /** Pausing is only offered during the fight; the celebration is short and runs itself. */
    public boolean isPausable() {
        boolean pausable = phase == Phase.FIGHTING;
        return pausable;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            renderer.drawPausedVeil();
            input.clear();
            return;
        }
        // Forget the paused interval so it is not replayed as simulation debt on resume.
        timestep.reset();
    }

    public boolean isPaused() {
        return paused;
    }
}
