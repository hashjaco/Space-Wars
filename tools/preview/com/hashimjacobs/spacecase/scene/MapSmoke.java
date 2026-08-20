package com.hashimjacobs.spacecase.scene;

import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.SaveGames;
import com.hashimjacobs.spacecase.prefs.Settings;

/** Starts the toolkit, draws the map, snapshots it. Proves the drawing code actually runs. */
public final class MapSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Assets.load();
                Preferences scratch = Preferences.userRoot()
                        .node("space-case-smoke-" + System.nanoTime());
                SaveGames saves = SaveGames.load(scratch);
                // A part-finished galaxy so all four node states appear at once.
                int galaxy = args.length > 1 ? Integer.parseInt(args[1]) : 0;
                int cleared = args.length > 2 ? Integer.parseInt(args[2]) : 3;
                // Everything before this galaxy, so it is unlocked, plus a few inside it.
                for (int i = 0; i < galaxy * Galaxy.LEVELS_PER_GALAXY + cleared; i++) {
                    saves.recordClear(GameMode.SOLO, Level.values()[i]);
                }

                Settings settings = Settings.load();
                SystemMapModel model =
                        new SystemMapModel(GameMode.SOLO, Galaxy.values()[galaxy], saves, null);
                SystemMapView view = new SystemMapView(model, settings);
                view.draw();
                System.out.println("focused: " + model.focused().level()
                        + " state " + model.focused().state());

                StackPane root = new StackPane(view.canvas());
                root.setPrefSize(996, 700);
                new Scene(root, 996, 700, Color.web("#0a0e1a"));
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.web("#0a0e1a"));
                WritableImage shot = root.snapshot(params, null);
                // Copied out by hand: javafx-swing is not a dependency and adding one just to
                // save a screenshot would be a worse trade than ten lines here.
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
                System.out.println("wrote " + args[0]);
                scratch.removeNode();
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
