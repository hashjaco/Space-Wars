package com.hashimjacobs.spacecase.engine;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A level's sky with and without the corner falloff, side by side in two files.
 *
 * The vignette is eight lines in {@link Renderer} and there is no way to judge it except to look
 * at it over real backdrop art: too strong and it eats the corners of the play area, too weak and
 * it may as well not be there. Takes {@code <out.png> [levelNumber] [0|1 vignette]}.
 */
public final class VignetteSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Assets.load();
                int number = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                boolean vignette = args.length <= 2 || !"0".equals(args[2]);
                Level level = Level.values()[number - 1];

                Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(Tokens.SPACE);
                gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
                for (Sprite layer : level.layers()) {
                    gc.drawImage(Assets.image(layer), 0, 0,
                            GameConfig.WIDTH, GameConfig.HEIGHT);
                }
                if (vignette) {
                    // The same paint Renderer builds, read back off it so the two cannot drift.
                    gc.setFill(Renderer.vignettePaint());
                    gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
                }

                StackPane root = new StackPane(canvas);
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Tokens.SPACE);
                WritableImage shot = root.snapshot(new SnapshotParameters(), null);
                int w = (int) shot.getWidth();
                int h = (int) shot.getHeight();
                java.awt.image.BufferedImage out =
                        new java.awt.image.BufferedImage(w, h,
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                javafx.scene.image.PixelReader pixels = shot.getPixelReader();
                for (int py = 0; py < h; py++) {
                    for (int px = 0; px < w; px++) {
                        out.setRGB(px, py, pixels.getArgb(px, py));
                    }
                }
                javax.imageio.ImageIO.write(out, "png", new java.io.File(args[0]));
                System.out.println("wrote " + args[0] + "  level " + number
                        + "  vignette " + vignette);
            } catch (Exception e) {
                failure[0] = e;
            } finally {
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
