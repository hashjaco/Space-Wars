package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javafx.animation.AnimationTimer;

import com.hashimjacobs.spacecase.GameConfig;
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

    private AnimationTimer timer;
    private boolean paused;
    private boolean finished;

    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Random random, Consumer<RoundResult> onRoundOver) {
        this.world = new World(mode);
        this.renderer = renderer;
        this.input = input;
        this.sounds = sounds;
        this.settings = settings;
        this.onRoundOver = onRoundOver;
        this.director = new SpawnDirector(random, settings.difficulty(), mode.rules());
        this.collisions = new CollisionSystem(sounds);
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
                step();
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    private void step() {
        if (paused || finished) {
            return;
        }

        for (ShipController controller : controllers) {
            controller.apply(input, world, sounds);
        }

        world.update();
        driveEnemies();
        director.update(world);
        collisions.resolve(world);
        world.sweep();
        renderer.draw(world, director);

        checkRoundOver();
    }

    private void driveEnemies() {
        int cooldown = settings.difficulty().enemyFireCooldown();
        for (EnemyShip enemy : world.enemies()) {
            PlayerShip target = world.nearestPlayer(enemy);
            if (target == null) {
                continue;
            }
            enemy.trackHorizontally(target);
            boolean onScreen = enemy.y() > -enemy.height() / 2;
            if (!onScreen || !enemy.tickWeapon(cooldown)) {
                continue;
            }
            fireEnemyShot(enemy);
        }
    }

    private void fireEnemyShot(EnemyShip enemy) {
        double x = enemy.centerX() - Sprite.ENEMY_BULLET.width() / 2;
        double y = enemy.y() + enemy.height();
        Bullet bullet = new Bullet(Sprite.ENEMY_BULLET, x, y, 0,
                GameConfig.ENEMY_BULLET_SPEED, null, GameConfig.ENEMY_BULLET_DAMAGE);
        world.addBullet(bullet);
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
        }
    }

    public boolean isPaused() {
        return paused;
    }
}
