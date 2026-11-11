package com.hashimjacobs.spacecase.onfoot;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.prefs.ScratchSettings;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * CaptureHarness for the on-foot stage: steps a real {@link OnFootStage}, draws it with the real
 * {@link OnFootRenderer}, writes raw BGRA per tick to stdout. Same seed, same film.
 *
 * The pilot is a scripted brawler: walk right until something is in reach, then punch/kick, with a
 * jump and a special now and then. Buttons are edge-triggered in Fighter, so attacks are pulsed.
 * ponytail: no dodging or guarding; re-roll the seed if the pilot goes down on camera.
 *
 * usage: OnFootCapture <MISSION> <ticks> [seed]   e.g. OnFootCapture FORGEWORKS 900 1
 */
public final class OnFootCapture {

    private static final int W = (int) GameConfig.WIDTH;
    private static final int H = (int) GameConfig.HEIGHT;

    public static void main(String[] args) throws Exception {
        Mission mission = Mission.valueOf(args[0].toUpperCase().replace('-', '_'));
        int ticks = Integer.parseInt(args[1]);
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        Platform.startup(() -> { });
        Exception[] failure = new Exception[1];
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                film(mission, ticks, seed);
            } catch (Exception e) {
                failure[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static void film(Mission mission, int ticks, long seed) throws Exception {
        Assets.load();
        Preferences node = Preferences.userRoot().node("space-case-capture-" + System.nanoTime());
        Settings settings = ScratchSettings.on(node);
        Canvas canvas = new Canvas(W, H);
        OnFootRenderer renderer = new OnFootRenderer(canvas.getGraphicsContext2D(), settings);
        StackPane root = new StackPane(canvas);
        new Scene(root, W, H, Tokens.SPACE);
        root.applyCss();
        root.layout();

        OnFootStage stage = new OnFootStage(seed, 1, Physics.DEFAULT, mission);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Tokens.SPACE);
        WritableImage shot = new WritableImage(W, H);
        byte[] frame = new byte[W * H * 4];
        OutputStream out = new BufferedOutputStream(System.out, frame.length);
        for (int tick = 0; tick < ticks && !stage.done(); tick++) {
            stage.step(List.of(pad(stage, tick)));
            renderer.draw(stage);
            root.snapshot(params, shot);
            shot.getPixelReader().getPixels(0, 0, W, H, PixelFormat.getByteBgraInstance(), frame, 0, W * 4);
            out.write(frame);
        }
        out.flush();
        node.removeNode();
    }

    private static Buttons pad(OnFootStage stage, int tick) {
        Fighter me = stage.pilots().get(0);
        boolean near = false;
        for (Fighter enemy : stage.enemies()) {
            if (enemy.health() > 0 && Math.abs(enemy.x() - me.x()) < 140) {
                near = true;
            }
        }
        boolean pulse = tick % 10 < 5;
        return new Buttons(false, !near, tick % 150 == 0, false,
                near && pulse && tick % 40 < 20, near && pulse && tick % 40 >= 20,
                near && tick % 240 < 5, false);
    }
}
