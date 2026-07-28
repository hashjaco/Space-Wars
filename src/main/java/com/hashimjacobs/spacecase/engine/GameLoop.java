package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.animation.AnimationTimer;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * The per-frame sequence: read input, simulate, resolve collisions, remove the dead, draw.
 *
 * Removal is deliberately a separate step that runs after collision handling, never during it.
 */
public final class GameLoop {

    private final World world;
    private final SpawnDirector director;
    private final CollisionSystem collisions;
    private final Renderer renderer;
    private final InputState input;
    private final SoundBank sounds;
    private final Settings settings;
    private final List<ShipController> controllers = new ArrayList<>();
    private final Consumer<RoundResult> onRoundOver;

    private final FixedTimestep timestep = new FixedTimestep();
    private AnimationTimer timer;
    private boolean paused;
    private boolean finished;
    private boolean bossMusicPlaying;

    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Random random, Consumer<RoundResult> onRoundOver) {
        this.world = new World(mode);
        this.renderer = renderer;
        this.input = input;
        this.sounds = sounds;
        this.settings = settings;
        this.onRoundOver = onRoundOver;
        this.director = new SpawnDirector(random, settings.difficulty(), mode.rules());
        this.collisions = new CollisionSystem(sounds, random);
        attachControllers();
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
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    /** One simulation step. Deliberately does no drawing -- {@link #frame} owns that. */
    private void step() {
        for (ShipController controller : controllers) {
            controller.apply(input, world, sounds);
        }

        world.update();
        driveEnemies();
        director.update(world);
        collisions.resolve(world);
        world.sweep();

        updateBossMusic();
        checkRoundOver();
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

    private void driveEnemies() {
        int difficultyCooldown = settings.difficulty().enemyFireCooldown();
        for (EnemyShip enemy : world.enemies()) {
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
            EnemyWeapons.fire(world, enemy, target);
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
        RoundResult result = RoundResult.of(world, director.wave());
        onRoundOver.accept(result);
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
