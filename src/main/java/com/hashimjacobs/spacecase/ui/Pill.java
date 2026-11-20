package com.hashimjacobs.spacecase.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

/**
 * Rounded rectangles drawn from images instead of filled as shapes.
 *
 * Every {@code fillRoundRect} and {@code strokeRoundRect} on a Canvas is rasterized on the CPU into
 * a mask that is uploaded to the GPU on its own. A meter is half a dozen of them and every damaged
 * enemy wears one, which on the Raspberry Pi 5 was the difference between a steady and a stuttering
 * fight. Here each box is drawn once, at the height and corner it is wanted, and laid down as three
 * slices -- the two rounded ends and a stretched middle -- so any width costs three image draws and
 * no masks. Same arguments as the Canvas calls, with the colour passed in rather than set first.
 */
public final class Pill {

    /** Oversampling of the drawn-once boxes, so they stay crisp on a 4K screen. */
    private static final int SCALE = 4;

    /** Flat run left in the middle of each box, in arena units: what the middle slice stretches. */
    private static final double FLAT = 4;

    private record Key(Color colour, int height, int arc, int line) {
    }

    /**
     * Least recently used, and bounded: a bar's colour can slide continuously between two, and each
     * shade is its own image.
     */
    private static final Map<Key, Image> BOXES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Image> eldest) {
            return size() > 256;
        }
    };

    private Pill() {
    }

    /** {@code fillRoundRect(x, y, w, h, arc, arc)} in {@code colour}. */
    public static void fill(GraphicsContext gc, Color colour, double x, double y, double w, double h, double arc) {
        draw(gc, colour, 0, x, y, w, h, arc);
    }

    /** {@code strokeRoundRect(x, y, w, h, arc, arc)} in {@code colour} at {@code lineWidth}. */
    public static void stroke(GraphicsContext gc, Color colour, double lineWidth,
                              double x, double y, double w, double h, double arc) {
        draw(gc, colour, lineWidth, x, y, w, h, arc);
    }

    private static void draw(GraphicsContext gc, Color colour, double line, double x, double y, double w, double h,
                             double arc) {
        if (w <= 0 || h <= 0) {
            return;
        }
        // As the Canvas does: a corner can be no larger than the box is high. Quantized to a quarter
        // unit so a box that breathes in size reuses a handful of images rather than making one a frame.
        int hq = quarters(h), aq = quarters(Math.min(arc, h)), lq = quarters(line);
        // Cached opaque, its own opacity laid on as alpha: the same picture for one flat colour,
        // and a bar that breathes by fading its colour does not make a new image every frame.
        Color opaque = colour.getOpacity() == 1 ? colour
                : Color.color(colour.getRed(), colour.getGreen(), colour.getBlue());
        Image box = BOXES.computeIfAbsent(new Key(opaque, hq, aq, lq), Pill::make);
        double alpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(alpha * colour.getOpacity());
        double height = hq / 4.0, corner = aq / 4.0, margin = margin(lq / 4.0);
        double cap = corner / 2 + margin;
        double outW = w + 2 * margin, outH = height + 2 * margin;
        if (outW <= 2 * cap) {
            // Narrower than its two ends: squeeze the whole box, as a rounded rect that short would be.
            gc.drawImage(box, x - margin, y - margin, outW, outH);
            gc.setGlobalAlpha(alpha);
            return;
        }
        double imageW = box.getWidth(), imageH = box.getHeight(), capPx = cap * SCALE;
        double left = x - margin, right = x + w + margin;
        // The seams on whole screen pixels: two slices meeting part-way across a pixel each cover
        // only part of it, and the sky shows through as a hairline.
        Affine screen = gc.getTransform();
        double seam1 = left + cap, seam2 = right - cap;
        if (screen.getMxy() == 0 && screen.getMyx() == 0 && screen.getMxx() > 0) {
            seam1 = snap(seam1, screen.getMxx(), screen.getTx());
            seam2 = Math.max(seam1, snap(seam2, screen.getMxx(), screen.getTx()));
        }
        gc.drawImage(box, 0, 0, capPx, imageH, left, y - margin, seam1 - left, outH);
        // The middle from the flat run's centre, a unit in from either end, so smoothing never
        // reaches the curve.
        gc.drawImage(box, capPx + SCALE, 0, imageW - 2 * capPx - 2 * SCALE, imageH,
                seam1, y - margin, seam2 - seam1, outH);
        gc.drawImage(box, imageW - capPx, 0, capPx, imageH, seam2, y - margin, right - seam2, outH);
        gc.setGlobalAlpha(alpha);
    }

    /** {@code value} moved to the nearest edge between screen pixels, under scale and offset. */
    private static double snap(double value, double scale, double offset) {
        return (Math.round(value * scale + offset) - offset) / scale;
    }

    private static int quarters(double value) {
        return (int) Math.round(value * 4);
    }

    /** Room around the box for a stroke, which is centred on the edge and so reaches past it. */
    private static double margin(double line) {
        return line <= 0 ? 0 : Math.ceil(line / 2 + 1);
    }

    private static Image make(Key key) {
        double height = key.height() / 4.0, corner = key.arc() / 4.0, line = key.line() / 4.0;
        double margin = margin(line);
        double width = corner + FLAT;
        Canvas canvas = new Canvas((width + 2 * margin) * SCALE, (height + 2 * margin) * SCALE);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(SCALE, SCALE);
        g.translate(margin, margin);
        if (line <= 0) {
            g.setFill(key.colour());
            g.fillRoundRect(0, 0, width, height, corner, corner);
        } else {
            g.setStroke(key.colour());
            g.setLineWidth(line);
            g.strokeRoundRect(0, 0, width, height, corner, corner);
        }
        SnapshotParameters clear = new SnapshotParameters();
        clear.setFill(Color.TRANSPARENT);
        return canvas.snapshot(clear, null);
    }
}
