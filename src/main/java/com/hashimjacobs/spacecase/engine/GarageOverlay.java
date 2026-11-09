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

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.ChassisArt;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.garage.GarageSession;
import com.hashimjacobs.spacecase.garage.Upgrade;

/**
 * The between-levels garage: a hangar deck, and a bay per pilot with their ship turning on a lift
 * pad above the counter. Whatever the cursor is browsing -- a frame, a paint job, a kit -- is what
 * turns on the pad, bought or not ({@link GarageSession#look}), so it can be seen before it is paid for.
 *
 * Canvas-drawn beside {@link Hud} and {@link DebriefOverlay} rather than built as JavaFX nodes, for
 * the same reason they are -- the router never swaps screens, so the world underneath stays alive
 * and the game is not torn down and rebuilt between every level.
 */
final class GarageOverlay {

    private static final Color BRAND = Tokens.BRAND;
    private static final Color LABEL = Tokens.LABEL;
    private static final Color MUTED = Tokens.TEXT_SECONDARY;
    private static final Color LOCKED = Tokens.TEXT_FAINT;
    private static final Color PANEL = Tokens.SURFACE_1;
    private static final Color PANEL_EDGE = Tokens.EDGE_STRONG;

    private static final double PANEL_WIDTH = 396;
    /** The counter starts under the lift pads; the ships turn above it, over the hangar deck. */
    private static final double PANEL_TOP = 318;
    /** Where each ship turns, and how big: the turntable frames are a three-quarter view. */
    private static final double PAD_Y = 196;
    private static final double SHOWCASE_SIZE = 210;

    /**
     * Sized to the rows it shows, plus the ship above them.
     *
     * Was 500, holding about eight rows, which the catalogue outgrew: fourteen upgrades plus
     * chassis, paint, kit and launch is eighteen. Rather than shrink the rows, the panel got taller
     * and the turntable got smaller. Sized to what it actually holds -- the stand, the rows, the
     * focused row's readout, and the two lines at the foot -- because a panel with a hand's width of
     * nothing at the bottom reads as unfinished rather than as spacious.
     */
    private static final double PANEL_HEIGHT = 508;
    private static final double PANEL_GAP = 24;
    /** Icon width plus the breath between it and the label. */
    private static final double ICON_GAP = 32;

    private static final double ROW_PITCH = Tokens.ROW_GARAGE;
    private static final double ROWS_TOP_OFFSET = 56;

    /** Where the focused row's before-and-after readout sits, and what it adds to that row. */
    private static final double DETAIL_OFFSET = 17;
    private static final double DETAIL_HEIGHT = 18;

    /** The level meter: one segment per level of the track. */
    private static final double SEGMENT_WIDTH = 13;
    private static final double SEGMENT_HEIGHT = 8;
    private static final double SEGMENT_GAP = 3;

    /** The drawn fallback hulls still spin flat: degrees per tick, about six seconds a turn. */
    private static final double SPIN_DEGREES_PER_TICK = 1.0;

    private final GraphicsContext gc;
    private final Font titleFont = Font.font(Tokens.BODY, FontWeight.BOLD, 30);
    private final Font detailFont = Font.font(Tokens.BODY, FontWeight.NORMAL, Tokens.SIZE_SMALL);
    private final Font headingFont = Font.font(Tokens.BODY, FontWeight.BOLD, 17);
    private final Font rowFont = Font.font(Tokens.BODY, FontWeight.NORMAL, 13);
    private final Font valueFont = Font.font(Tokens.BODY, FontWeight.BOLD, 15);
    private final Font promptFont = Font.font(Tokens.BODY, FontWeight.BOLD, 13);

    GarageOverlay(GraphicsContext gc) {
        this.gc = gc;
    }

    /**
     * @param tick the phase's own tick count, not a per-draw counter -- draws happen once per
     *             display refresh while the simulation steps at a fixed rate, so accumulating here
     *             would spin the ship twice as fast on a 120 Hz monitor
     */
    void draw(GarageSession session, int tick) {
        // The hangar over the frozen level, lightly veiled so the counters and text sit forward of it.
        gc.drawImage(Assets.image(Sprite.GARAGE_BAY), 0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
        gc.setFill(Tokens.veil(0.35));
        gc.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(titleFont);
        gc.setFill(BRAND);
        gc.fillText("GARAGE", GameConfig.WIDTH / 2, 58);
        gc.setFont(rowFont);
        gc.setFill(MUTED);
        gc.fillText("Up/down choose    left/right try on a frame, paint or kit    fire to buy",
                GameConfig.WIDTH / 2, GameConfig.HEIGHT - 26);

        int bays = session.bayCount();
        double totalWidth = bays * PANEL_WIDTH + (bays - 1) * PANEL_GAP;
        double x = (GameConfig.WIDTH - totalWidth) / 2;
        for (int bay = 0; bay < bays; bay++) {
            drawBay(session, bay, x, tick);
            x += PANEL_WIDTH + PANEL_GAP;
        }
    }

    private void drawBay(GarageSession session, int bay, double x, int tick) {
        drawShowcase(session.look(bay), x + PANEL_WIDTH / 2, PAD_Y, tick);

        gc.save();
        gc.setGlobalAlpha(0.92);
        gc.setFill(PANEL);
        gc.fillRoundRect(x, PANEL_TOP, PANEL_WIDTH, PANEL_HEIGHT, 12, 12);
        gc.restore();
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

        y = PANEL_TOP + ROWS_TOP_OFFSET;
        List<GarageSession.Row> rows = session.rows(bay);
        int cursor = session.cursor(bay);
        // Only the slice on screen. rows() still hands back all of them, which is what keeps the
        // row indices the session and its tests speak in unchanged.
        int first = session.firstVisibleRow(bay);
        int visible = session.visibleRows(bay);
        for (int i = first; i < Math.min(rows.size(), first + visible); i++) {
            if ((i == GarageSession.firstUpgradeRow() || i == GarageSession.launchRow()) && i > first) {
                // The looks, the upgrades and the way out are three different kinds of row.
                gc.setStroke(PANEL_EDGE);
                gc.setLineWidth(1);
                y += 12;
                gc.strokeLine(left - 6, y - 24, right + 6, y - 24);
            }
            y = drawRow(left, right, y, rows.get(i), i == cursor, i == GarageSession.launchRow());
        }
        if (session.scrolls(bay)) {
            drawScrollTrack(right + 10, PANEL_TOP + ROWS_TOP_OFFSET - 12,
                    visible * ROW_PITCH, first, visible, rows.size());
        }

        String message = session.message(bay);
        if (!message.isEmpty()) {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(rowFont);
            gc.setFill(Tokens.DANGER_SOFT);
            gc.fillText(message, left, y + 8);
        }

        if (session.done(bay)) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(promptFont);
            gc.setFill(MUTED);
            gc.fillText("READY - waiting for the other pilot",
                    x + PANEL_WIDTH / 2, PANEL_TOP + PANEL_HEIGHT - 26);
        }
    }

    /**
     * The ship on its lift pad, turning: a full revolution of the three-quarter turntable render,
     * in the browsed paint, with the browsed kit bolted on. Not yet bought, it says so.
     */
    private void drawShowcase(GarageSession.Look look, double centreX, double centreY, int tick) {
        double size = SHOWCASE_SIZE;
        // The pad: a lit ring on the deck, and a cone of light from the gantry above it.
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);
        gc.setGlobalAlpha(0.10);
        gc.setFill(Color.web("#ffd9a0"));
        gc.fillPolygon(new double[] {centreX - 26, centreX + 26, centreX + size * 0.62, centreX - size * 0.62},
                new double[] {centreY - size * 0.95, centreY - size * 0.95, centreY + size * 0.22, centreY + size * 0.22}, 4);
        gc.setGlobalAlpha(0.30);
        gc.setFill(BRAND);
        gc.fillOval(centreX - size * 0.62, centreY + size * 0.12, size * 1.24, size * 0.22);
        gc.restore();
        gc.setStroke(Color.web("#ffb347"));
        gc.setLineWidth(2);
        gc.setGlobalAlpha(0.8);
        gc.strokeOval(centreX - size * 0.55, centreY + size * 0.14, size * 1.1, size * 0.18);
        gc.setGlobalAlpha(1);

        ChassisArt art = ChassisArt.named(look.chassis().name());
        if (art != null) {
            Image frame = Assets.chassisFrames(art, look.kit().slug(), ChassisArt.View.TURN, look.livery().hue(),
                    look.livery().saturation(), 2, false).get(ChassisArt.View.TURN.frameIndexAt(tick));
            gc.drawImage(frame, centreX - size / 2, centreY - size / 2, size, size);
        } else {
            // The drawn hulls are flat art: they spin flat, decal and all.
            Sprite hull = look.chassis().pose(look.livery(), 2, false, false);
            Sprite kit = look.kit().overlay(2, false);
            double w = hull.width() * 2, h = hull.height() * 2;
            gc.save();
            gc.translate(centreX, centreY);
            gc.rotate(tick * SPIN_DEGREES_PER_TICK);
            gc.drawImage(Assets.image(hull), -w / 2, -h / 2, w, h);
            if (kit != null) {
                gc.drawImage(Assets.image(kit), -w / 2, -h / 2, w, h);
            }
            gc.restore();
        }

        if (!look.owned()) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(promptFont);
            gc.setFill(Tokens.DANGER_SOFT);
            gc.fillText("PREVIEW", centreX, centreY + size * 0.46);
        }
    }

    private double drawRow(double left, double right, double y, GarageSession.Row row,
                           boolean focused, boolean isLaunch) {
        if (focused) {
            gc.setFill(Tokens.SURFACE_2);
            gc.fillRoundRect(left - 10, y - 4, right - left + 20, 22, 5, 5);
        }

        // Upgrade rows carry an icon ahead of the label; the cosmetic rows keep the bare label, so
        // the two kinds read differently before a word is read.
        double textLeft = left;
        if (row.upgrade() != null) {
            Sprite icon = iconFor(row.upgrade());
            gc.drawImage(Assets.image(icon), left, y - 15, icon.width(), icon.height());
            textLeft = left + ICON_GAP;
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(focused || isLaunch ? valueFont : rowFont);
        gc.setFill(isLaunch ? BRAND : focused ? Color.WHITE : MUTED);
        gc.fillText(row.label(), textLeft, y);

        if (isLaunch) {
            return y + ROW_PITCH;
        }

        boolean ladder = row.maxLevel() > 0;
        if (ladder) {
            drawMeter(left + ICON_GAP + 122, y - 8, row);
        }

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(rowFont);
        if (row.maxed()) {
            // Spelled out, not just coloured green: a maxed track and an unaffordable one must not
            // be told apart by hue alone.
            gc.setFill(BRAND);
            gc.fillText(ladder ? "MAX" : row.value(), right, y);
        } else {
            // The cosmetic rows carry the name of whatever is being browsed, and it has to stay
            // visible next to the price -- without it a pilot is buying a paint job blind.
            String price = (ladder ? "" : row.value() + "   ") + row.cost() + " CR";
            if (row.affordable()) {
                gc.setFill(Color.WHITE);
                gc.fillText(price, right, y);
            } else {
                // A leading dash as well as the muted colour, so affordability is not colour alone.
                gc.setFill(LOCKED);
                gc.fillText("- " + price, right, y);
            }
        }

        if (!focused || row.detail().isEmpty()) {
            return y + ROW_PITCH;
        }
        // The readout sits in its own band under the row rather than in the next row's, which is
        // what it did at first -- the line ran straight through the meter below it.
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(detailFont);
        gc.setFill(Tokens.TEXT_DIM);
        gc.fillText(row.detail(), left, y + DETAIL_OFFSET);
        return y + ROW_PITCH + DETAIL_HEIGHT;
    }

    /**
     * One segment per level, filled for what is bought.
     *
     * Replaces a bar of ASCII hashes and dots. The segment after the last filled one is outlined
     * dashed when the next level is affordable and left plain when it is not, so whether a pilot can
     * buy is visible in the meter's shape rather than only in the colour of the price.
     */
    private void drawMeter(double x, double y, GarageSession.Row row) {
        for (int i = 0; i < row.maxLevel(); i++) {
            double sx = x + i * (SEGMENT_WIDTH + SEGMENT_GAP);
            boolean filled = i < row.level();
            boolean nextUp = i == row.level() && row.affordable();
            if (filled) {
                gc.setFill(BRAND);
                gc.fillRoundRect(sx, y, SEGMENT_WIDTH, SEGMENT_HEIGHT,
                        Tokens.RADIUS_S, Tokens.RADIUS_S);
                continue;
            }
            gc.setStroke(nextUp ? BRAND : PANEL_EDGE);
            gc.setLineWidth(1);
            gc.setLineDashes(nextUp ? new double[]{3, 2} : null);
            gc.strokeRoundRect(sx, y, SEGMENT_WIDTH, SEGMENT_HEIGHT,
                    Tokens.RADIUS_S, Tokens.RADIUS_S);
            gc.setLineDashes(null);
        }
    }

    /** Where the visible slice sits in the whole list, for a bay that no longer shows all of it. */
    private void drawScrollTrack(double x, double y, double height, int first, int visible,
                                 int total) {
        gc.setFill(Tokens.TRACK);
        gc.fillRoundRect(x, y, 4, height, 2, 2);
        double thumb = height * visible / total;
        double offset = height * first / total;
        gc.setFill(PANEL_EDGE);
        gc.fillRoundRect(x, y + offset, 4, thumb, 2, 2);
    }

    /** One icon per upgrade; the switch is exhaustive, so a new Upgrade without art fails to compile. */
    static Sprite iconFor(Upgrade upgrade) {
        return switch (upgrade) {
            case FIREPOWER -> Sprite.GARAGE_FIREPOWER;
            case FIRE_RATE -> Sprite.GARAGE_FIRE_RATE;
            case SALVO -> Sprite.GARAGE_SALVO;
            case FOCUS -> Sprite.GARAGE_FOCUS;
            case HONE -> Sprite.GARAGE_HONE;
            case CHOKE -> Sprite.GARAGE_CHOKE;
            case YIELD -> Sprite.GARAGE_YIELD;
            case HULL -> Sprite.GARAGE_HULL;
            case SHIELDING -> Sprite.GARAGE_SHIELDING;
            case CAPACITOR -> Sprite.GARAGE_CAPACITOR;
            case REPAIR -> Sprite.GARAGE_REPAIR;
            case SPEED -> Sprite.GARAGE_SPEED;
            case EJECT -> Sprite.GARAGE_EJECT;
            case COLLECTOR -> Sprite.GARAGE_COLLECTOR;
        };
    }
}
