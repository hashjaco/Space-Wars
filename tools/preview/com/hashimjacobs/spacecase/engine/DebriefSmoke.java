package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Rank;
import com.hashimjacobs.spacecase.prefs.Standing;

/**
 * Draws the debrief for N pilots at a given reveal tick: {@code <out.png> <pilots> <tick>}.
 *
 * Four pilots is the case to look at -- the columns once ran off the stage -- and a tick part-way
 * through shows the reveal mid-count rather than only its finished state.
 */
public final class DebriefSmoke {
    public static void main(String[] args) throws Exception {
        int pilots = Integer.parseInt(args[1]);
        int tick = Integer.parseInt(args[2]);
        Platform.startup(() -> { });
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Assets.load();
                String[] names = {"ACE", "NOVA", "LONGER PILOT NAME", "KESTREL"};
                List<Debrief> debriefs = new ArrayList<>();
                List<Standing> standings = new ArrayList<>();
                for (int i = 0; i < pilots; i++) {
                    Debrief.Tally before = new Debrief.Tally(0, 0, 0, 0, 0, 3);
                    Debrief.Tally after = new Debrief.Tally(34 - i * 9, 12, 400, 320 - i * 70, i * 45, 3 - Math.min(2, i));
                    debriefs.add(Debrief.of(names[i], 10, 5, 9000, before, after, 7200 + i * 900));
                    standings.add(new Standing(names[i], Rank.values()[3 + i * 2], 14_250 + i * 9000, i == 0));
                }
                Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
                canvas.getGraphicsContext2D().setFill(Color.web("#22304a"));
                canvas.getGraphicsContext2D().fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
                DebriefOverlay overlay = new DebriefOverlay(canvas.getGraphicsContext2D());
                overlay.draw(Level.THE_FROZEN_HEART, debriefs, standings, tick, true);
                // Met now: every enemy and core 1; met before: the clock; new: every enemy and core 1.
                overlay.drawChallenges(tick, debriefs, 0b000101, 0b000101, 0b000111);
                StackPane root = new StackPane(canvas);
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT);
                WritableImage shot = root.snapshot(new SnapshotParameters(), null);
                int w = (int) shot.getWidth(), h = (int) shot.getHeight();
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        out.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                    }
                }
                javax.imageio.ImageIO.write(out, "png", new java.io.File(args[0]));
                System.out.println("wrote " + args[0] + ", reveal ends at tick " + DebriefOverlay.revealEnd(debriefs));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }
}
