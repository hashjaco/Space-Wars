package com.hashimjacobs.spacecase.engine;

import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.garage.GarageSession;
import com.hashimjacobs.spacecase.garage.Loadout;

/**
 * The between-levels garage: a bay per pilot, each with their ship turning on a stand.
 *
 * Canvas-drawn beside {@link Hud} and {@link DebriefOverlay} rather than built as JavaFX nodes, for
 * the same reason they are -- the router never swaps screens, so the world underneath stays alive
 * and the game is not torn down and rebuilt between every level.
 */
final class GarageOverlay {

    private static final Color BRAND = Color.web("#0ec417");
    private static final Color LABEL = Color.web("#b388ff");
    private static final Color MUTED = Color.web("#9fb0c9");
    private static final Color LOCKED = Color.web("#6d7a90");
    private static final Color PANEL = Color.web("#0c1120");
    private static final Color PANEL_EDGE = Color.web("#3b4560");

    private static final double PANEL_WIDTH = 396;
    private static final double PANEL_TOP = 132;
    /** Sized to the eight rows plus the ship above them; any taller and the panel reads unfinished. */
    private static final double PANEL_HEIGHT = 500;
    private static final double PANEL_GAP = 24;

    /** The turntable: degrees per tick, about six seconds for a full revolution. */
    private static final double SPIN_DEGREES_PER_TICK = 1.0;

    /** How much bigger than its in-flight size the ship is shown on the stand. */
    private static final double SHOWCASE_SCALE = 2.2;

    private final GraphicsContext gc;
    private final Font titleFont = Font.font("Verdana", FontWeight.BOLD, 30);
    private final Font headingFont = Font.font("Verdana", FontWeight.BOLD, 17);
    private final Font rowFont = Font.font("Verdana", FontWeight.NORMAL, 13);
    private final Font valueFont = Font.font("Verdana", FontWeight.BOLD, 15);
    private final Font promptFont = Font.font("Verdana", FontWeight.BOLD, 13);

    GarageOverlay(GraphicsContext gc) {
        this.gc = gc;
    }

    /**
     * @param tick the phase's own tick count, not a per-draw counter -- draws happen once per
     *             display refresh while the simulation steps at a fixed rate, so accumulating here
     *             would spin the ship twice as fast on a 120 Hz monitor
     */
    void draw(GarageSession session, int tick) {
        gc.setFill(Color.color(0, 0, 0, 0.82));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(titleFont);
        gc.setFill(BRAND);
        gc.fillText("GARAGE", GameConfig.WIDTH / 2, 58);
        gc.setFont(rowFont);
        gc.setFill(MUTED);
        gc.fillText("Up/down choose    left/right browse paint and kits    fire to buy",
                GameConfig.WIDTH / 2, 98);

        int bays = session.bayCount();
        double totalWidth = bays * PANEL_WIDTH + (bays - 1) * PANEL_GAP;
        double x = (GameConfig.WIDTH - totalWidth) / 2;
        for (int bay = 0; bay < bays; bay++) {
            drawBay(session, bay, x, tick);
            x += PANEL_WIDTH + PANEL_GAP;
        }
    }

    private void drawBay(GarageSession session, int bay, double x, int tick) {
        gc.setFill(PANEL);
        gc.fillRoundRect(x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, 12, 12);
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, 12, 12);

        double left = x + 22;
        double right = x + PANEL_WIDTH - 22;
        double y = PANEL_TOP + 18;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(headingFont);
        gc.setFill(Color.WHITE);
        gc.fillText(session.pilotName(bay), left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(valueFont);
        gc.setFill(BRAND);
        gc.fillText(session.credits(bay) + " CR", right, y);

        drawShowcase(session.loadout(bay), x + PANEL_WIDTH / 2, PANEL_TOP + 128, tick);

        y = PANEL_TOP + 210;
        List<GarageSession.Row> rows = session.rows(bay);
        int cursor = session.cursor(bay);
        for (int i = 0; i < rows.size(); i++) {
            y = drawRow(left, right, y, rows.get(i), i == cursor, i == GarageSession.launchRow());
        }

        String message = session.message(bay);
        if (!message.isEmpty()) {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(rowFont);
            gc.setFill(Color.web("#ff6b6b"));
            gc.fillText(message, left, y + 8);
        }

        if (session.done(bay)) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(promptFont);
            gc.setFill(MUTED);
            gc.fillText("READY - waiting for the other pilot",
                    x + PANEL_WIDTH / 2, PANEL_TOP + PANEL_HEIGHT - 30);
        }
    }

    /**
     * The ship on its stand, turning.
     *
     * A flat rotation about the sprite's centre. The hull art is a top-down view, so spinning it
     * this way genuinely shows every side of what there is to see -- the alternative, faking a
     * turntable by squashing horizontally, has only five bank poses to work with and reads as a
     * glitch rather than as a rotation.
     * ponytail: swap in squash plus the bank frames if this ever needs to look three-dimensional.
     */
    private void drawShowcase(Loadout loadout, double centreX, double centreY, int tick) {
        double w = Sprite.P1_STRAIGHT.width() * SHOWCASE_SCALE;
        double h = Sprite.P1_STRAIGHT.height() * SHOWCASE_SCALE;

        // A pool of light under the stand, so the ship is not floating in a black rectangle.
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setGlobalAlpha(0.22);
        gc.setFill(BRAND);
        gc.fillOval(centreX - w * 0.62, centreY + h * 0.26, w * 1.24, h * 0.30);
        gc.restore();

        // Lean.NONE is index 2: the straight-ahead pose, which is the one worth showing off.
        Sprite hull = loadout.livery().pose(2, false);
        Sprite kit = loadout.kit().overlay(2);

        gc.save();
        gc.translate(centreX, centreY);
        gc.rotate(tick * SPIN_DEGREES_PER_TICK);
        Image hullImage = Assets.image(hull);
        gc.drawImage(hullImage, -w / 2, -h / 2, w, h);
        if (kit != null) {
            // Inside the same transform, so the decal turns with the hull it is bolted to.
            gc.drawImage(Assets.image(kit), -w / 2, -h / 2, w, h);
        }
        gc.restore();
    }

    private double drawRow(double left, double right, double y, GarageSession.Row row,
                           boolean focused, boolean isLaunch) {
        if (focused) {
            gc.setFill(Color.web("#1b2440"));
            gc.fillRoundRect(left - 10, y - 4, right - left + 20, 22, 5, 5);
        }

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(focused || isLaunch ? valueFont : rowFont);
        gc.setFill(isLaunch ? BRAND : focused ? Color.WHITE : MUTED);
        gc.fillText(row.label(), left, y);

        if (isLaunch) {
            return y + 30;
        }

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(rowFont);
        if (row.maxed()) {
            gc.setFill(BRAND);
            gc.fillText(row.value(), right, y);
        } else {
            // Grey out what the pilot cannot afford, so the whole shelf reads at a glance.
            gc.setFill(row.affordable() ? Color.WHITE : LOCKED);
            gc.fillText(row.value() + "   " + row.cost() + " CR", right, y);
        }
        return y + 30;
    }
}
