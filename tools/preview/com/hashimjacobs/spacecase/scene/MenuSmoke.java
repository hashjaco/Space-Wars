package com.hashimjacobs.spacecase.scene;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Snapshots the start menu, backdrop and title and all.
 *
 * Written for the pass that swapped the menu backdrop off {@code L1_MID} -- the level-one layer
 * with nothing in it -- and dropped the box from around the heading. Both are changes you can only
 * judge by looking, which is what this directory is for.
 *
 * The rows are the real start menu's, at their worst case rather than a short sample: a Continue
 * row carrying the longest save line the game can produce, and a locked row. A five-row stub of
 * made-up short labels was what let a row that could never fit its own text ship in the first
 * place, so the point of this harness is that the hard case is the one in the picture.
 */
public final class MenuSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Assets.load();

                // The longest line SaveSlot.describe() can build: widest mode, last level, the
                // longest level label in the game, a loop count and an eight-digit score.
                MenuButton resume = new MenuButton("Continue",
                        "Single Player \u00b7 Lv50 Mag-Storm Caverns \u00b7 loop 9 \u00b7 12,345,678",
                        () -> { });
                MenuButton locked = new MenuButton("Endless Run",
                        "locked -- clear the campaign first", () -> { });
                locked.setLocked(true);

                MenuPanel panel = new MenuPanel(
                        resume,
                        new MenuButton("Universe Map", () -> { }),
                        new MenuButton("Single Player", () -> { }),
                        locked,
                        new MenuButton("Multiplayer", () -> { }),
                        new MenuButton("Load Game", () -> { }),
                        new MenuButton("Pilots", () -> { }),
                        new MenuButton("Settings", () -> { }),
                        new MenuButton("Help", () -> { }),
                        new MenuButton("Exit", () -> { }));
                // Focused, so the render also shows the selection cue rather than ten idle rows.
                resume.setHighlighted(true);

                StackPane root = MenuScreen.build("SPACE CASE",
                        MenuScreen.decal(Sprite.P1_STRAIGHT, 54),
                        panel,
                        MenuScreen.caption("Best solo run: 0", Tokens.SIZE_BODY, Tokens.TEXT_DIM));
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Tokens.SPACE);
                root.applyCss();
                root.layout();

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Tokens.SPACE);
                WritableImage shot = root.snapshot(params, null);
                // Copied out by hand for the same reason MapSmoke does it: javafx-swing is not a
                // dependency and adding one just to save a screenshot would be the worse trade.
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
                System.out.println("wrote " + args[0] + "  " + w + "x" + h);
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
