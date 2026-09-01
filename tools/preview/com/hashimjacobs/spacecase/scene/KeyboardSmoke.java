package com.hashimjacobs.spacecase.scene;

import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.ScratchPilots;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Snapshots the Pilots screen, shut and with the on-screen keyboard open over it.
 *
 * The card is the one part of this feature no test can look at. {@code KeyGridModelTest} proves the
 * cursor lands on the cell it should and proves nothing about whether that cell is on the screen.
 * The two questions the picture answers are whether five rows of eight plus an action row and a
 * preview line still fit an 864px stage, and whether the scrim reaches the title rather than
 * stopping at the panel -- which is why the card mounts into the screen root and not into the panel.
 *
 * <b>Drawn at its worst case.</b> The name alphabet rather than a code one: 37 characters is the
 * layout with a short last row, and a ten-character name is the longest the preview line can hold.
 *
 * The pilots come from a throwaway node. {@code Pilots.load()} is this machine's real seat names, and
 * a capture run must not rename whoever is actually flying.
 */
public final class KeyboardSmoke {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: KeyboardSmoke <outDir>");
            System.exit(2);
        }
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Preferences scratch = Preferences.userRoot().node("space-case-preview-" + System.nanoTime());

        Platform.runLater(() -> {
            try {
                Assets.load();
                write(pilots(scratch, false), args[0] + "/pilots.png");
                write(pilots(scratch, true), args[0] + "/pilots-keyboard.png");
            } catch (Exception e) {
                failure[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        scratch.removeNode();
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
    }

    /**
     * The real screen, not a stand-in.
     *
     * A fresh panel each time: the card mounts into the root, and a screen can only have one root,
     * so the open and shut pictures cannot be taken from the same one.
     *
     * @param open whether to press Enter on the focused seat, which is what a pad's A button sends
     */
    private static StackPane pilots(Preferences store, boolean open) {
        Pilots seats = ScratchPilots.on(store);
        // The longest name the field accepts, so the preview line is drawn at full width.
        seats.setName(1, "CONSTANCEX");
        seats.setName(2, "GOOSE");

        NameEntryPanel panel = new NameEntryPanel(seats, () -> { });
        StackPane root = MenuScreen.build("PILOTS", panel,
                MenuScreen.caption("Names show under your ship. Rank is earned across every run.",
                        Tokens.SIZE_SMALL, Tokens.TEXT_FAINT));
        panel.setOverlayHost(root);
        if (open) {
            panel.handleKey(KeyCode.ENTER);
        }
        return root;
    }

    /** Lifted from {@code BoardSmoke}, which is where this directory's capture idiom lives. */
    private static void write(StackPane root, String path) throws Exception {
        new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Tokens.SPACE);
        root.applyCss();
        root.layout();

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Tokens.SPACE);
        WritableImage shot = root.snapshot(params, null);
        int w = (int) shot.getWidth();
        int h = (int) shot.getHeight();
        java.awt.image.BufferedImage out =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader pixels = shot.getPixelReader();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                out.setRGB(px, py, pixels.getArgb(px, py));
            }
        }
        javax.imageio.ImageIO.write(out, "png", new java.io.File(path));
        System.out.println("wrote " + path + "  " + w + "x" + h);
    }
}
