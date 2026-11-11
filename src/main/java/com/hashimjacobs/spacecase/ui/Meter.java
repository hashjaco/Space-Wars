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
        gc.setFill(Tokens.veil(0.6));
        gc.fillRoundRect(x - 2, y - 2, width + 4, height + 4, radius + 2, radius + 2);
        gc.setFill(track);
        gc.fillRoundRect(x, y, width, height, radius, radius);
        if (ghost > f) {
            gc.setFill(Tokens.GHOST);
            gc.fillRoundRect(x, y, width * Math.min(1, ghost), height, radius, radius);
        }
        gc.setFill(fill);
        gc.fillRoundRect(x, y, width * f, height, radius, radius);
        if (hatched) {
            stripes(gc, x, y, width * f, height);
        }
        gc.setStroke(Tokens.veil(0.55));
        gc.setLineWidth(Tokens.STROKE);
        for (int i = 1; i < segments; i++) {
            double tx = Math.round(x + width * i / segments) + 0.5;
            gc.strokeLine(tx, y, tx, y + height);
        }
        gc.setStroke(Tokens.EDGE_STRONG);
        gc.setLineWidth(Tokens.STROKE_HAIR);
        gc.strokeRoundRect(x, y, width, height, radius, radius);
    }

    /**
     * Diagonal hatching over a bar's fill. Static on purpose: it is a state, not an alarm.
     */
    public static void stripes(GraphicsContext gc, double x, double y, double width, double height) {
        if (width <= 0) {
            return;
        }
        gc.save();
        gc.beginPath();
        gc.rect(x, y, width, height);
        gc.clip();
        gc.setStroke(Tokens.veil(0.45));
        gc.setLineWidth(2);
        for (double sx = x - height; sx < x + width; sx += 6) {
            gc.strokeLine(sx, y + height, sx + height, y);
        }
        gc.restore();
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
