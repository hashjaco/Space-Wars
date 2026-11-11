package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Snapshots the menu chrome: {@code <out.png> menu|results}.
 *
 * "menu" is a worst-case list -- a two-line row carrying the longest save line the game can
 * produce, a locked row, a focused row, and enough rows to scroll -- because a stub of short labels
 * is what once let a row that could not fit its own text ship. "results" is the game-over card as
 * {@code SceneRouter.showResults} composes it, from the same shared pieces.
 */
public final class MenuSmoke {
    public static void main(String[] args) throws Exception {
        boolean results = args.length > 1 && args[1].equals("results");
        Platform.startup(() -> { });
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Assets.load();
                StackPane root;
                if (results) {
                    Text score = new Text("1,284,650");
                    score.setFont(Font.font(Assets.displayFontFamily(), 64));
                    score.setFill(Tokens.BRAND);
                    VBox card = MenuScreen.card(
                            MenuScreen.caption("SINGLE PLAYER", Tokens.SIZE_SMALL, Tokens.LABEL), score,
                            MenuScreen.caption("★  NEW BEST", Tokens.SIZE_ROW, Tokens.WARN),
                            MenuScreen.caption("Level 30   The Frozen Heart   ·   41 waves", Tokens.SIZE_BODY, Tokens.TEXT_DIM));
                    MenuButton onward = new MenuButton("On to Tempest", () -> { });
                    onward.setHighlighted(true);
                    root = MenuScreen.build("GALAXY CLEARED", card,
                            new MenuPanel(onward, new MenuButton("Main Menu", () -> { })));
                } else {
                    List<MenuButton> rows = new ArrayList<>();
                    rows.add(new MenuButton("Continue",
                            "Single Player - Lv50 Mag-Storm Caverns - loop 9 - 12,345,678", () -> { }));
                    rows.add(new MenuButton("Play", () -> { }));
                    MenuButton locked = new MenuButton("IV   Tempest", () -> { });
                    locked.setLocked(true);
                    rows.add(locked);
                    for (int i = 0; i < 12; i++) {
                        rows.add(new MenuButton("Row " + (i + 4), () -> { }));
                    }
                    rows.get(1).setHighlighted(true);
                    root = MenuScreen.build("EXTRAS", new MenuPanel(rows.toArray(new MenuButton[0])));
                }
                new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Tokens.SPACE);
                root.applyCss();
                root.layout();
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Tokens.SPACE);
                WritableImage shot = root.snapshot(params, null);
                int w = (int) shot.getWidth(), h = (int) shot.getHeight();
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        out.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                    }
                }
                javax.imageio.ImageIO.write(out, "png", new java.io.File(args[0]));
                System.out.println("wrote " + args[0] + "  " + w + "x" + h);
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
