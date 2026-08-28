package com.hashimjacobs.spacecase.scene;

import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.prefs.Account;
import com.hashimjacobs.spacecase.prefs.ScratchAccount;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * Snapshots the two online screens: the score board, and cloud save.
 *
 * Both are the kind of thing the suite cannot see. The board is the longest panel in the game --
 * ten entries plus two rows is exactly {@code MenuPanel}'s twelve-row window, so it sits on the
 * boundary where the next row added starts scrolling and the one after that is the {@code LAUNCH}
 * button this directory's README records scrolling off the bottom of the garage.
 *
 * <b>Drawn at their worst case, not a sample.</b> Ten-character pilot names against eight-digit
 * scores on every row, because that is the pair that decides whether a row can fit its own text,
 * and a stub of short made-up labels is precisely what let an unfittable row ship before.
 *
 * The account is a throwaway one. {@code Account.load()} would mint and keep this machine's real
 * sync code, and that is the one value in the game that must never be written into a PNG.
 */
public final class BoardSmoke {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: BoardSmoke <outDir>");
            System.exit(2);
        }
        Platform.startup(() -> { });
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Preferences scratch = Preferences.userRoot().node("space-case-preview-" + System.nanoTime());

        Platform.runLater(() -> {
            try {
                Assets.load();
                write(board(), args[0] + "/board.png");
                write(boardWaiting(), args[0] + "/board-empty.png");
                write(cloud(ScratchAccount.on(scratch)), args[0] + "/cloud.png");
                write(replaceConfirm(), args[0] + "/cloud-confirm.png");
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

    /** A full board: the widest name the entry screen accepts against the widest score. */
    private static StackPane board() {
        String[] names = {"CONSTANCEX", "NOVA", "ACE", "MAGNETARIC", "PIP",
                          "WRECKINGXY", "HAL", "SOLARWINDS", "JET", "ZED"};
        int[] scores = {12_345_678, 9_004_112, 880_450, 640_000, 512_300,
                        410_004, 288_000, 96_500, 12_400, 900};
        MenuButton[] rows = new MenuButton[names.length + 2];
        for (int at = 0; at < names.length; at++) {
            rows[at] = new MenuButton("", () -> { });
            rows[at].setRow(String.valueOf(at + 1),
                    names[at] + "   " + String.format("%,d", scores[at]));
            rows[at].setLocked(true);
        }
        rows[rows.length - 2] = new MenuButton("Co-op board", () -> { });
        rows[rows.length - 1] = new MenuButton("Back", () -> { });
        // Focused where the router puts the cursor: the first row anybody can press, not row one.
        rows[rows.length - 2].setHighlighted(true);

        return MenuScreen.build("SINGLE PLAYER BOARD", new MenuPanel(rows),
                MenuScreen.caption("Every run you finish is posted under your pilot's name.",
                        Tokens.SIZE_SMALL, Tokens.TEXT_FAINT));
    }

    /** The state every new player sees first, and the one a stubbed preview would never draw. */
    private static StackPane boardWaiting() {
        MenuButton[] rows = new MenuButton[12];
        for (int at = 0; at < 10; at++) {
            rows[at] = new MenuButton("", () -> { });
            rows[at].setRow(String.valueOf(at + 1), at == 0 ? "nothing posted yet" : "");
            rows[at].setLocked(true);
        }
        rows[10] = new MenuButton("Co-op board", () -> { });
        rows[11] = new MenuButton("Back", () -> { });
        rows[10].setHighlighted(true);

        return MenuScreen.build("SINGLE PLAYER BOARD", new MenuPanel(rows),
                MenuScreen.caption("Every run you finish is posted under your pilot's name.",
                        Tokens.SIZE_SMALL, Tokens.TEXT_FAINT));
    }

    /**
     * The real panel, not a stand-in: it is package-private, which is what this class is in this
     * package for. Half a code is typed into the other-code field, because a half-entered code is
     * what the underscores are there to make legible.
     */
    private static StackPane cloud(Account account) {
        CloudPanel panel = new CloudPanel(account, profile -> { }, () -> { });
        // Up onto the other-code row, which is the only row that takes letters, then type into it.
        panel.handleKey(javafx.scene.input.KeyCode.UP);
        for (char typed : "K7QP".toCharArray()) {
            panel.handleKey(javafx.scene.input.KeyCode.getKeyCode(String.valueOf(typed)));
        }
        return MenuScreen.build("CLOUD SAVE", panel,
                MenuScreen.caption("Upload here, then type this code on your other machine.",
                        Tokens.SIZE_SMALL, Tokens.TEXT_FAINT),
                MenuScreen.caption("Anyone with the code has the profile. Read it to nobody else.",
                        Tokens.SIZE_CAPTION, Tokens.TEXT_GHOST));
    }

    /** The screen that stands between a download and somebody's campaign. */
    private static StackPane replaceConfirm() {
        MenuButton keep = new MenuButton("No, keep this machine's progress", () -> { });
        keep.setHighlighted(true);
        return MenuScreen.build("REPLACE PROGRESS",
                new MenuPanel(keep, new MenuButton("Yes, replace it with the download", () -> { })),
                MenuScreen.caption("The downloaded profile replaces this machine's runs, pilots "
                        + "and high scores. This cannot be undone.",
                        Tokens.SIZE_SMALL, Tokens.TEXT_FAINT),
                MenuScreen.caption("Settings and controls stay as they are on this machine.",
                        Tokens.SIZE_CAPTION, Tokens.TEXT_GHOST));
    }

    /**
     * Lays a screen out and writes it, copying the snapshot pixel by pixel for the reason every
     * other harness here does: {@code javafx-swing} is not a dependency and adding one to save a
     * screenshot would be the worse trade.
     */
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

    private BoardSmoke() {
    }
}
