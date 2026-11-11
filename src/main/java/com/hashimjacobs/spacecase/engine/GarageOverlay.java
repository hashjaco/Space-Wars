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

    /**
     * Each bay is a ship column and a sidebar, side by side.
     *
     * The ship turns on its pad on the left with room to be looked at; the menu is a column down
     * the right, tabs at its top and LAUNCH pinned to its foot. Two pilots split the screen and each
     * gets the same arrangement at a smaller scale.
     */
    private static final double MARGIN = 16;
    private static final double BAY_GAP = 16;
    private static final double SIDEBAR_TOP = 96;
    private static final double SIDEBAR_BOTTOM_GAP = 48;
    private static final double SIDEBAR_WIDE = 420;
    private static final double SIDEBAR_NARROW = 286;
    private static final double SHOWCASE_WIDE = 400;
    private static final double SHOWCASE_NARROW = 176;
    private static final double TABS_OFFSET = 50;
    private static final double TAB_HEIGHT = 28;
    /** Icon width plus the breath between it and the label. */
    private static final double ICON_GAP = 32;

    private static final double ROW_PITCH = Tokens.ROW_GARAGE;
    private static final double ROWS_TOP_OFFSET = 110;

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
        gc.fillText("Left/right on the tabs change category    up/down choose    left/right try on a look    fire to buy",
                GameConfig.WIDTH / 2, GameConfig.HEIGHT - 26);

        int bays = session.bayCount();
        double bayWidth = (GameConfig.WIDTH - 2 * MARGIN - (bays - 1) * BAY_GAP) / Math.max(1, bays);
        boolean narrow = bays > 1;
        for (int bay = 0; bay < bays; bay++) {
            drawBay(session, bay, MARGIN + bay * (bayWidth + BAY_GAP), bayWidth, narrow, tick);
        }
    }

    private void drawBay(GarageSession session, int bay, double x, double width, boolean narrow, int tick) {
        double sideWidth = narrow ? SIDEBAR_NARROW : SIDEBAR_WIDE;
        double sideX = x + width - sideWidth;
        double top = SIDEBAR_TOP;
        double height = GameConfig.HEIGHT - SIDEBAR_TOP - SIDEBAR_BOTTOM_GAP;

        // The ship column: everything left of the sidebar, the pad a little above its middle.
        double size = Math.min(narrow ? SHOWCASE_NARROW : SHOWCASE_WIDE, sideX - x - 20);
        drawShowcase(session.look(bay), x + (sideX - x) / 2, top + height * 0.42, size, tick);

        gc.save();
        gc.setGlobalAlpha(0.92);
        gc.setFill(PANEL);
        gc.fillRoundRect(sideX, top, sideWidth, height, 12, 12);
        gc.restore();
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(sideX, top, sideWidth, height, 12, 12);

        double left = sideX + 20;
        double right = sideX + sideWidth - 20;
        double y = top + 16;

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(headingFont);
        gc.setFill(Color.WHITE);
        gc.fillText(session.pilotName(bay), left, y);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(valueFont);
        gc.setFill(BRAND);
        gc.fillText(session.credits(bay) + " CR", right, y);

        drawTabs(session.tab(bay), session.cursor(bay) < 0, left + 6, top + TABS_OFFSET, right - left - 12);

        List<GarageSession.Row> rows = session.rows(bay);
        int cursor = session.cursor(bay);
        y = top + ROWS_TOP_OFFSET;
        int launch = rows.size() - 1;
        for (int i = 0; i < launch; i++) {
            y = drawRow(left, right, y, rows.get(i), i == cursor, false);
        }

        // LAUNCH is pinned to the foot of the sidebar, so the way out never moves between tabs.
        double launchY = top + height - 44;
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(left - 6, launchY - 14, right + 6, launchY - 14);
        drawRow(left, right, launchY, rows.get(launch), cursor == launch, true);

        String message = session.message(bay);
        if (!message.isEmpty()) {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFont(rowFont);
            gc.setFill(Tokens.DANGER_SOFT);
            gc.fillText(message, left, launchY - 40);
        }

        if (session.done(bay)) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(promptFont);
            gc.setFill(MUTED);
            gc.fillText("READY - waiting for the other pilot", sideX + sideWidth / 2, top + height + 8);
        }
    }

    /**
     * The category strip. The current tab is filled; with the cursor on the strip it is outlined in
     * the brand colour and flanked by arrows, which is the cue that left and right now change tab.
     */
    private void drawTabs(GarageSession.Tab current, boolean focused, double x, double y, double width) {
        GarageSession.Tab[] tabs = GarageSession.Tab.values();
        double tabWidth = width / tabs.length;
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            double tx = x + i * tabWidth;
            boolean selected = tabs[i] == current;
            if (selected) {
                gc.setFill(Tokens.SURFACE_2);
                gc.fillRoundRect(tx + 2, y, tabWidth - 4, TAB_HEIGHT, 6, 6);
                if (focused) {
                    gc.setStroke(BRAND);
                    gc.setLineWidth(1.5);
                    gc.strokeRoundRect(tx + 2, y, tabWidth - 4, TAB_HEIGHT, 6, 6);
                }
            }
            gc.setFont(selected ? valueFont : rowFont);
            gc.setFill(selected ? (focused ? BRAND : Color.WHITE) : MUTED);
            // Squeezed to its slot: four tabs share the panel, and WEAPONS is wider than a quarter of it.
            gc.fillText(tabs[i].label().toUpperCase(), tx + tabWidth / 2, y + 6, tabWidth - 8);
        }
        if (focused) {
            gc.setFont(valueFont);
            gc.setFill(BRAND);
            gc.fillText("<", x - 6, y + 6);
            gc.fillText(">", x + width + 6, y + 6);
        }
        gc.setStroke(PANEL_EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(x, y + TAB_HEIGHT + 8, x + width, y + TAB_HEIGHT + 8);
    }

    private void drawShowcase(GarageSession.Look look, double centreX, double centreY, double size, int tick) {
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
            gc.drawImage(Assets.image(icon), left, y + 8 - icon.height() / 2, icon.width(), icon.height());
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
            drawMeter(right - 64 - row.maxLevel() * (SEGMENT_WIDTH + SEGMENT_GAP), y + 8 - SEGMENT_HEIGHT / 2, row);
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
