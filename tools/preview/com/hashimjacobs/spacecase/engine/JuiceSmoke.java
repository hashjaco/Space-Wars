package com.hashimjacobs.spacecase.engine;

import java.util.Random;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.ScratchSettings;

/**
 * One staged mid-fight frame with every feel effect on at once: a hull lit by a hit, a flagship
 * likewise, sparks, embers, a muzzle glow, a hit-spark, the flagship's shockwave, a white-out half
 * faded, and a combo on the HUD. Looked at rather than reasoned about.
 */
public final class JuiceSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        Preferences node = Preferences.userRoot().node("space-case-scratch-juice");
        Platform.runLater(() -> {
            try {
                Assets.load();
                Level level = Level.values()[0];
                World world = new World(GameMode.SOLO);
                world.enterLevel(level);
                PlayerShip pilot = world.players().get(0);
                pilot.setPosition(460, 640);
                pilot.takeDamage(80); // under a quarter hull: arcs, smoke and the shorting frames

                EnemyShip cruiser = new EnemyShip(EnemyShip.EnemyKind.CRUISER,
                        level.enemySprite(EnemyShip.EnemyKind.CRUISER), 300, 260);
                world.addEnemy(cruiser);
                cruiser.takeDamage(1);
                EnemyShip boss = new EnemyShip(Boss.SENTINEL, 560, 70);
                world.addEnemy(boss);
                boss.takeDamage(1);

                world.emit(Particle.Kind.SPARK, cruiser.centerX(), cruiser.centerY() + 30, 14, 6);
                world.emit(Particle.Kind.EMBER, 200, 450, 30, 4);
                world.emit(Particle.Kind.MUZZLE, pilot.centerX(), pilot.y(), 2, 1.5);
                world.addEffect(Explosion.HIT_SPARK, cruiser.centerX(), cruiser.centerY() + 30, 28);
                world.addEffect(Explosion.BOSS_RING, 200, 450, 420);
                world.addEffect(Explosion.LARGE, 200, 450, 160);
                world.flash();
                for (int i = 0; i < 7; i++) {
                    world.tickScenery();
                }
                pilot.addKillScore(100, world.tick());
                pilot.addKillScore(100, world.tick());
                pilot.addKillScore(100, world.tick());

                SpawnDirector director = new SpawnDirector(new Random(1), Difficulty.NORMAL,
                        GameMode.SOLO.rules());
                Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
                Renderer renderer = new Renderer(canvas.getGraphicsContext2D(), ScratchSettings.on(node));
                renderer.draw(world, director);

                StackPane root = new StackPane(canvas);
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Color.BLACK);
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.BLACK);
                WritableImage shot = root.snapshot(params, null);
                int w = (int) shot.getWidth();
                int h = (int) shot.getHeight();
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                javafx.scene.image.PixelReader pixels = shot.getPixelReader();
                for (int py = 0; py < h; py++) {
                    for (int px = 0; px < w; px++) {
                        out.setRGB(px, py, pixels.getArgb(px, py));
                    }
                }
                javax.imageio.ImageIO.write(out, "png", new java.io.File(args[0]));
                System.out.println("wrote " + args[0] + ", particles " + world.particles().size()
                        + ", effects " + world.explosions().size() + ", flash " + world.flashRemaining());
            } catch (Exception e) {
                failure[0] = e;
            } finally {
                try {
                    node.removeNode();
                } catch (Exception ignored) {
                    // scratch node
                }
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
    }
}
