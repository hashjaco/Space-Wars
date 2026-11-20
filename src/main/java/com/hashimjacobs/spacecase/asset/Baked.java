package com.hashimjacobs.spacecase.asset;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

/**
 * Effects baked into images once, so drawing them each frame is a plain image draw.
 *
 * On the Canvas, a draw under {@code gc.setEffect} or any blend mode other than SRC_OVER is
 * rendered to a scratch texture and composited back. On a tiled GPU like the Raspberry Pi 5's
 * every one of those is a flush, and a fight makes hundreds a frame: that, not the simulation, is
 * what slowed the game as the bullets piled up. Each effect here is the same arithmetic the effect
 * does, run once per source image on the CPU.
 *
 * Keyed by the source image's identity ({@link Image} does not override equals), and weakly, so a
 * level's frames dropped by {@link Assets#preload} take their baked copies with them. Asked for
 * on the FX thread, made on a background one. Plain ARGB kernels underneath, no JavaFX, so the
 * rules are testable without a toolkit.
 */
public final class Baked {

    /** Source pixels the glow spills past each edge: Glow's blur radius of 10, and a little. */
    public static final int GLOW_PAD = 12;

    /** {@code new Glow(0.35)}, as the hulls were drawn under. */
    private static final float GLOW_LEVEL = 0.35f;

    private static final long SHADOW = 1L << 60, GLOW = 2L << 60, SCREEN = 3L << 60, TINT = 4L << 60;

    /** Marks a bake that has been asked for and not yet come back. */
    private static final Object PENDING = new Object();

    /** Every bake of a source, by kind (and for a tint, its amounts). FX thread only. */
    private static final Map<Image, Map<Long, Object>> BAKED = new WeakHashMap<>();
    private static final Map<Image, Image> WHITES = new WeakHashMap<>();

    /**
     * One low-priority thread does the baking, so a 512 px flagship frame never costs the frame
     * that first shows it. Until a bake lands the caller draws the way it always did, with the live
     * effect, so there is no frame where the look is missing.
     */
    private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "bake");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private Baked() {
    }

    /** The image's own silhouette in black, {@code ColorAdjust(0, 0, -1, 0)}; null until baked. */
    public static Image shadowOf(Image source) {
        return later(source, SHADOW, (argb, w, h) -> new Baking(silhouette(argb), w, h));
    }

    /**
     * The image under {@code Glow(0.35)}, on a canvas {@link #GLOW_PAD} larger on every side so the
     * spill is not cut off at the sprite's edge; null until baked. Draw it over the source's box
     * grown by the pad.
     */
    public static Image glowOf(Image source) {
        return later(source, GLOW, (argb, w, h) ->
                new Baking(glow(argb, w, h, GLOW_PAD, GLOW_LEVEL), w + 2 * GLOW_PAD, h + 2 * GLOW_PAD));
    }

    /**
     * What the image adds to whatever is under it when drawn under SCREEN, as an image to draw
     * under plain alpha; null until baked. Exact for the brightest channel of every pixel, which
     * on a flame is the one that shows.
     */
    public static Image screenLike(Image source) {
        return later(source, SCREEN, (argb, w, h) -> new Baking(screenLike(argb), w, h));
    }

    /** The image under {@code ColorAdjust(hue, saturation, 0, 0)}; null until baked. */
    public static Image hueShifted(Image source, double hue, double saturation) {
        long amounts = (Math.round(hue * 1000) & 0xffffL) << 16 | (Math.round(saturation * 1000) & 0xffffL);
        return later(source, TINT | amounts,
                (argb, w, h) -> new Baking(hueRotate(argb, (float) hue, (float) saturation), w, h));
    }

    /**
     * The same shape in white, for a hit flash laid over the frame. Made on the spot rather than
     * later: a hit flash is a few frames long, and the fallback would be the very effect it replaces.
     */
    public static Image whiteOf(Image source) {
        if (!ready(source)) {
            return source;
        }
        return WHITES.computeIfAbsent(source, s -> same(s, white(pixels(s))));
    }

    /** The arithmetic of one bake: source pixels in, result pixels and size out. No JavaFX. */
    private interface Kernel {
        Baking apply(int[] argb, int width, int height);
    }

    private record Baking(int[] argb, int width, int height) {
    }

    /**
     * Only the arithmetic runs on the baking thread. The pixels are read, and the image made, on
     * the FX thread: an image made elsewhere was drawn as whatever the texture memory last held,
     * a block of coloured stripes where the hull's shadow should be.
     */
    private static final Map<Color, Image> DISCS = new HashMap<>();
    private static final int DISC_PX = 64;

    /**
     * A filled disc in one colour, edge anti-aliased, to draw in place of {@code fillOval}.
     *
     * Every filled or stroked shape on the Canvas is rasterized on the CPU into a mask that is then
     * uploaded to the GPU; on the Raspberry Pi 5 those uploads were most of the render thread's
     * time. A disc as an image is drawn as a textured quad, with no mask at all. Stretch it for an
     * ellipse; give the colour opaque and fade with the global alpha.
     */
    public static Image disc(Color colour) {
        return DISCS.computeIfAbsent(colour, c -> image(disc(argbOf(c), DISC_PX), DISC_PX, DISC_PX));
    }

    private static int argbOf(Color c) {
        return (int) Math.round(c.getOpacity() * 255) << 24 | (int) Math.round(c.getRed() * 255) << 16
                | (int) Math.round(c.getGreen() * 255) << 8 | (int) Math.round(c.getBlue() * 255);
    }

    /** Coverage of a circle filling the square, one pixel of falloff at the rim. */
    static int[] disc(int argb, int size) {
        int[] out = new int[size * size];
        double radius = size / 2.0, alpha = (argb >>> 24) / 255.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double d = Math.hypot(x + 0.5 - radius, y + 0.5 - radius);
                double cover = Math.max(0, Math.min(1, radius - d + 0.5));
                if (cover > 0) {
                    out[y * size + x] = (int) Math.round(cover * alpha * 255) << 24 | (argb & 0xffffff);
                }
            }
        }
        return out;
    }

    private static final Map<Paint, Image> PAINTS = new java.util.IdentityHashMap<>();

    /**
     * A gradient paint filled into a {@code width} by {@code height} image, to stretch over the box
     * it was meant to fill. For a proportional gradient that is the same picture. A large rect
     * filled with a gradient was the costliest single draw left on the Pi, at over ten
     * milliseconds each time; the image is an ordinary draw. Keyed by the paint's identity, so pass
     * the same paint object each frame.
     */
    public static Image paint(Paint paint, int width, int height) {
        return PAINTS.computeIfAbsent(paint, p -> {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(width, height);
            canvas.getGraphicsContext2D().setFill(p);
            canvas.getGraphicsContext2D().fillRect(0, 0, width, height);
            javafx.scene.SnapshotParameters clear = new javafx.scene.SnapshotParameters();
            clear.setFill(Color.TRANSPARENT);
            return canvas.snapshot(clear, null);
        });
    }

    /**
     * Shapes that never change, drawn once at {@code scale} onto a scratch canvas and kept as an
     * image to lay down with {@code drawImage} over a {@code w} by {@code h} box. A shape filled or
     * stroked on the game canvas is rasterized into a mask and uploaded to the GPU each time.
     */
    public static Image prerender(double w, double h, double scale,
                                  java.util.function.Consumer<javafx.scene.canvas.GraphicsContext> draw) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(
                Math.ceil(w * scale), Math.ceil(h * scale));
        javafx.scene.canvas.GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(scale, scale);
        draw.accept(g);
        javafx.scene.SnapshotParameters clear = new javafx.scene.SnapshotParameters();
        clear.setFill(Color.TRANSPARENT);
        return canvas.snapshot(clear, null);
    }

    private static Image later(Image source, long key, Kernel kernel) {
        if (!ready(source)) {
            return null;    // still decoding in the background
        }
        Map<Long, Object> bakes = BAKED.computeIfAbsent(source, s -> new HashMap<>());
        Object done = bakes.get(key);
        if (done instanceof Image image) {
            return image;
        }
        if (done == null) {
            bakes.put(key, PENDING);
            int[] argb = pixels(source);
            int w = (int) source.getWidth(), h = (int) source.getHeight();
            BAKER.execute(() -> {
                Baking result = kernel.apply(argb, w, h);
                Platform.runLater(() -> bakes.put(key, image(result.argb(), result.width(), result.height())));
            });
        }
        return null;
    }

    private static boolean ready(Image image) {
        return image.getProgress() >= 1 && !image.isError() && image.getPixelReader() != null;
    }

    private static int[] pixels(Image image) {
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] argb = new int[w * h];
        PixelReader reader = image.getPixelReader();
        reader.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return argb;
    }

    private static Image same(Image source, int[] argb) {
        return image(argb, (int) source.getWidth(), (int) source.getHeight());
    }

    private static Image image(int[] argb, int w, int h) {
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return out;
    }

    static int[] silhouette(int[] argb) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            out[i] = argb[i] & 0xff000000;
        }
        return out;
    }

    static int[] white(int[] argb) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int alpha = argb[i] >>> 24;
            out[i] = alpha == 0 ? 0 : alpha << 24 | 0x00ffffff;
        }
        return out;
    }

    /**
     * Glow is the input added to a Gaussian blur of itself (radius 10, sigma about 3.3) at
     * {@code level}, in premultiplied colour. Three box passes of half-width 3 stand in for the
     * Gaussian (sigma about 3.5), with the result clamped as ADD clamps.
     */
    static int[] glow(int[] argb, int w, int h, int pad, float level) {
        int pw = w + 2 * pad, ph = h + 2 * pad;
        float[][] channels = new float[4][pw * ph];    // premultiplied a, r, g, b
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                float a = (p >>> 24) / 255f;
                int i = (y + pad) * pw + x + pad;
                channels[0][i] = a;
                channels[1][i] = ((p >> 16) & 0xff) / 255f * a;
                channels[2][i] = ((p >> 8) & 0xff) / 255f * a;
                channels[3][i] = (p & 0xff) / 255f * a;
            }
        }
        int[] out = new int[pw * ph];
        float[][] blurred = new float[4][];
        float[] scratch = new float[pw * ph];
        for (int c = 0; c < 4; c++) {
            float[] b = channels[c].clone();
            for (int pass = 0; pass < 3; pass++) {
                boxRows(b, scratch, pw, ph, 3);
                boxColumns(scratch, b, pw, ph, 3);
            }
            blurred[c] = b;
        }
        for (int i = 0; i < out.length; i++) {
            float a = Math.min(1, channels[0][i] + level * blurred[0][i]);
            // The running sums leave float dust where the image is empty, either side of zero.
            // Below half a step of alpha is clear; and a channel a hair below zero, shifted into
            // place, would set the alpha bits and paint the empty canvas in opaque streaks.
            if (a < 0.5f / 255) {
                continue;
            }
            float r = Math.max(0, Math.min(a, channels[1][i] + level * blurred[1][i]));
            float g = Math.max(0, Math.min(a, channels[2][i] + level * blurred[2][i]));
            float bl = Math.max(0, Math.min(a, channels[3][i] + level * blurred[3][i]));
            out[i] = Math.round(a * 255) << 24 | Math.round(r / a * 255) << 16
                    | Math.round(g / a * 255) << 8 | Math.round(bl / a * 255);
        }
        return out;
    }

    private static void boxRows(float[] in, float[] out, int w, int h, int r) {
        float scale = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0;
            for (int x = -r; x <= r; x++) {
                sum += x >= 0 && x < w ? in[row + x] : 0;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = sum * scale;
                int add = x + r + 1, drop = x - r;
                sum += (add < w ? in[row + add] : 0) - (drop >= 0 ? in[row + drop] : 0);
            }
        }
    }

    private static void boxColumns(float[] in, float[] out, int w, int h, int r) {
        float scale = 1f / (2 * r + 1);
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int y = -r; y <= r; y++) {
                sum += y >= 0 && y < h ? in[y * w + x] : 0;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum * scale;
                int add = y + r + 1, drop = y - r;
                sum += (add < h ? in[add * w + x] : 0) - (drop >= 0 ? in[drop * w + x] : 0);
            }
        }
    }

    /**
     * SCREEN over d gives {@code d + c*a - d*c*a} per channel. Plain alpha of colour c' at alpha a'
     * gives {@code c'*a' + d*(1 - a')}. With {@code a' = a*max(c)} and {@code c' = c/max(c)} the
     * two agree on the brightest channel and on what is added.
     */
    static int[] screenLike(int[] argb) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
            int max = Math.max(r, Math.max(g, b));
            int alpha = (p >>> 24) * max / 255;
            if (alpha == 0) {
                continue;
            }
            out[i] = alpha << 24 | r * 255 / max << 16 | g * 255 / max << 8 | b * 255 / max;
        }
        return out;
    }

    /**
     * ColorAdjust's hue and saturation: in HSB, the hue turned by half of {@code hue}, and the
     * saturation pushed toward 1 by a positive {@code saturation} or scaled down by a negative one.
     */
    static int[] hueRotate(int[] argb, float hue, float saturation) {
        int[] out = new int[argb.length];
        float[] hsb = new float[3];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if (p >>> 24 == 0) {
                continue;
            }
            HullPaint.rgbToHsb((p >> 16) & 0xff, (p >> 8) & 0xff, p & 0xff, hsb);
            float h = hsb[0] + hue / 2;
            h -= (float) Math.floor(h);
            float s = saturation > 0 ? hsb[1] + saturation * (1 - hsb[1]) : hsb[1] * (1 + saturation);
            out[i] = (p & 0xff000000) | (HullPaint.hsbToRgb(h, s, hsb[2]) & 0x00ffffff);
        }
        return out;
    }
}
