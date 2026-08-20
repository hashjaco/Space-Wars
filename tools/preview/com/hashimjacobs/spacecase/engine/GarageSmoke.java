package com.hashimjacobs.spacecase.engine;

import java.util.List;
import java.util.Set;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.garage.GarageSession;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.garage.Upgrade;

/** Draws the garage so the layout can be looked at rather than reasoned about. */
public final class GarageSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        Platform.runLater(() -> {
            try {
                Assets.load();
                GarageSession.Seat one = new GarageSession.Seat("ACE",
                        Set.of(KeyCode.W), Set.of(KeyCode.S), Set.of(KeyCode.A),
                        Set.of(KeyCode.D), Set.of(KeyCode.SHIFT));
                GarageSession.Seat two = new GarageSession.Seat("NOVA",
                        Set.of(KeyCode.UP), Set.of(KeyCode.DOWN), Set.of(KeyCode.LEFT),
                        Set.of(KeyCode.RIGHT), Set.of(KeyCode.ENTER));
                Loadout bought = Loadout.stock(1);
                bought.raise(Upgrade.FIREPOWER);
                bought.raise(Upgrade.FIREPOWER);
                bought.raise(Upgrade.FIREPOWER);
                bought.raise(Upgrade.FIRE_RATE);
                bought.raise(Upgrade.SALVO);
                bought.raise(Upgrade.SALVO);
                bought.raise(Upgrade.HULL);
                GarageSession session = new GarageSession(List.of(one, two),
                        List.of(380, 90), List.of(bought, Loadout.stock(2)));
                // Move the first bay's cursor down so a focused row with its readout is visible.
                for (int i = 0; i < 4; i++) {
                    session.handleKey(KeyCode.S);
                }

                Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
                GarageOverlay overlay = new GarageOverlay(canvas.getGraphicsContext2D());
                canvas.getGraphicsContext2D().setFill(Color.web("#0a0e1a"));
                canvas.getGraphicsContext2D().fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
                overlay.draw(session, 40);

                StackPane root = new StackPane(canvas);
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Color.web("#0a0e1a"));
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.web("#0a0e1a"));
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
                System.out.println("rows total " + session.rows(0).size()
                        + ", visible " + session.visibleRows(0)
                        + ", scrolls " + session.scrolls(0)
                        + ", catalogue " + Upgrade.catalogueCost() + " CR");
                System.out.println("wrote " + args[0]);
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
