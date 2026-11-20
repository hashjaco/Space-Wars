package com.hashimjacobs.spacecase.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * The one way a bar is drawn: player health, the special, the flagship, the bars over hulls.
 *
 * A bar is read by its fill against its track, so every one gets the same dark frame to separate
 * it from whatever sky is behind, the same segment ticks so a level can be counted rather than
 * estimated, and -- where the caller keeps a {@link Lag} -- a pale ghost of what was just lost.
 */
public final class Meter {

    private Meter() {
    }

    /**
     * @param ghost    where the bar stood a moment ago; pass {@code fraction} for no ghost
     * @param segments equal divisions to tick, or 0 for a plain bar
     * @param hatched  diagonal hatching over the fill, the colour-blind-safe half of "critical"
     */
    public static void draw(GraphicsContext gc, double x, double y, double width, double height,
                            double fraction, double ghost, Color fill, Color track,
                            int segments, boolean hatched) {
        double radius = Math.min(Tokens.RADIUS, height);
        double f = Math.max(0, Math.min(1, fraction));
        // Rounded boxes from images and ticks as thin rects: no shape masks, see Pill.
        Pill.fill(gc, Tokens.veil(0.6), x - 2, y - 2, width + 4, height + 4, radius + 2);
        Pill.fill(gc, track, x, y, width, height, radius);
        if (ghost > f) {
            Pill.fill(gc, Tokens.GHOST, x, y, width * Math.min(1, ghost), height, radius);
        }
        Pill.fill(gc, fill, x, y, width * f, height, radius);
        if (hatched) {
            stripes(gc, x, y, width * f, height);
        }
        gc.setFill(Tokens.veil(0.55));
        for (int i = 1; i < segments; i++) {
            double tx = Math.round(x + width * i / segments) + 0.5;
            gc.fillRect(tx - Tokens.STROKE / 2, y, Tokens.STROKE, height);
        }
        Pill.stroke(gc, Tokens.EDGE_STRONG, Tokens.STROKE_HAIR, x, y, width, height, radius);
    }

    /** The hatching, drawn once per bar height and kept as an image. */
    private static final java.util.Map<Integer, javafx.scene.image.Image> HATCHES = new java.util.HashMap<>();
    /** A whole number of stripes, so lengths of it laid end to end continue the pattern. */
    private static final double HATCH_LENGTH = 510;
    private static final int HATCH_SCALE = 4;

    /**
     * Diagonal hatching over a bar's fill. Static on purpose: it is a state, not an alarm.
     *
     * From an image cut to the fill's width. Drawn live, it was a clip and a stroked line every
     * six pixels, every frame the bar was low -- on the Raspberry Pi 5, a quarter-second frame
     * for as long as a pilot was close to death.
     */
    public static void stripes(GraphicsContext gc, double x, double y, double width, double height) {
        if (width <= 0) {
            return;
        }
        javafx.scene.image.Image hatch = HATCHES.computeIfAbsent((int) Math.round(height * 4),
                quarters -> hatch(quarters / 4.0));
        for (double from = 0; from < width; from += HATCH_LENGTH) {
            double drawn = Math.min(width - from, HATCH_LENGTH);
            gc.drawImage(hatch, 0, 0, drawn * HATCH_SCALE, height * HATCH_SCALE, x + from, y, drawn, height);
        }
    }

    /** Lines from the bar's bottom-left to top-right every six units, starting a height before x. */
    private static javafx.scene.image.Image hatch(double height) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(
                HATCH_LENGTH * HATCH_SCALE, height * HATCH_SCALE);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(HATCH_SCALE, HATCH_SCALE);
        g.setStroke(Tokens.veil(0.45));
        g.setLineWidth(2);
        for (double sx = -height; sx < HATCH_LENGTH; sx += 6) {
            g.strokeLine(sx, height, sx + height, 0);
        }
        javafx.scene.SnapshotParameters clear = new javafx.scene.SnapshotParameters();
        clear.setFill(Color.TRANSPARENT);
        return canvas.snapshot(clear, null);
    }

    /**
     * The ghost's memory: holds where a bar was for a beat after a loss, then drains to meet it.
     *
     * Drawing state only -- stepped once per drawn frame, never read by the simulation -- so it
     * cannot disturb lockstep. It is motion in one direction, not a flash.
     */
    public static final class Lag {
        private static final int HOLD_FRAMES = 20;
        private static final double DRAIN_PER_FRAME = 0.012;

        private double value = 1;
        private double last = 1;
        private int hold;

        public double follow(double target) {
            if (target >= value) {
                value = target;
                hold = 0;
            } else {
                if (target < last) {
                    hold = HOLD_FRAMES;
                }
                if (hold > 0) {
                    hold--;
                } else {
                    value = Math.max(target, value - DRAIN_PER_FRAME);
                }
            }
            last = target;
            return value;
        }
    }

    /** A demo of the lag: holds, then drains, then snaps up on a heal. Run with -ea. */
    public static void main(String[] args) {
        Lag lag = new Lag();
        lag.follow(1);
        assert lag.follow(0.5) == 1 : "holds on the frame of the hit";
        double v = 1;
        for (int i = 0; i < 200; i++) {
            v = lag.follow(0.5);
        }
        assert v == 0.5 : "drains to the target";
        assert lag.follow(0.9) == 0.9 : "a heal is immediate";
        System.out.println("Meter.Lag ok");
    }
}
