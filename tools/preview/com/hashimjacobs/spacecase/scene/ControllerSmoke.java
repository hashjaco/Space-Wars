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
import com.hashimjacobs.spacecase.prefs.ScratchSettings;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.ControllerLayout;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Snapshots the controller screen once per layout, and once with a row waiting for a button.
 *
 * {@code ControllerLayoutTest} proves every button can be clicked and none overlaps another. It
 * cannot say whether an outline looks like the pad it is named for, or whether the action tags
 * collide -- this is where that gets looked at.
 *
 * No pad reader: the drawing is unlit, which is how it looks with no controller connected.
 */
public final class ControllerSmoke {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: ControllerSmoke <outDir>");
            System.exit(2);
        }
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Preferences scratch = Preferences.userRoot().node("space-case-preview-" + System.nanoTime());

        Platform.runLater(() -> {
            try {
                Assets.load();
                for (ControllerLayout layout : ControllerLayout.values()) {
                    write(screen(scratch, layout, false),
                            args[0] + "/controller-" + layout.name().toLowerCase() + ".png");
                }
                write(screen(scratch, ControllerLayout.XBOX, true), args[0] + "/controller-armed.png");
                // The Settings pages the controller screen hangs off, front page and one category.
                Settings settings = ScratchSettings.on(scratch);
                for (int page = 0; page < 2; page++) {
                    SettingsPanel panel = new SettingsPanel(settings, null, () -> "P1 Xbox Controller",
                            () -> { }, () -> { }, () -> { });
                    if (page == 1) {
                        for (KeyCode key : new KeyCode[] {KeyCode.DOWN, KeyCode.DOWN, KeyCode.DOWN,
                                KeyCode.ENTER}) {
                            panel.handleKey(key);
                        }
                    }
                    write(MenuScreen.build("SETTINGS", panel),
                            args[0] + "/settings-" + (page == 0 ? "front" : "controller") + ".png");
                }
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

    /** @param armed whether to walk down to Fire and choose it, leaving the row waiting */
    private static StackPane screen(Preferences store, ControllerLayout layout, boolean armed) {
        Settings settings = ScratchSettings.on(store);
        settings.setPadLayout(1, layout.name());
        ControllerPanel panel = new ControllerPanel(settings, null, () -> { });
        MenuNavigator navigator = panel.navigator(() -> { });
        navigator.handleKey(KeyCode.DOWN);
        navigator.handleKey(KeyCode.DOWN);
        if (armed) {
            navigator.handleKey(KeyCode.ENTER);
            panel.tick(0);
            panel.tick(2_400_000_000L);     // partway through the countdown
        }
        return MenuScreen.build("CONTROLLER", panel,
                MenuScreen.caption("Choose an action, then hold the button you want for a second, "
                        + "or click it on the drawing. The stick and d-pad always move.",
                        12, Tokens.TEXT_FAINT));
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
