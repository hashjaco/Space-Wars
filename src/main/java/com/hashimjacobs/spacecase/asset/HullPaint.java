package com.hashimjacobs.spacecase.asset;

import java.awt.Color;

/**
 * Everything a CG hull is flown in, from its green loops on disk: the paint, the bank and the hit
 * flash. The generator bakes these for the drawn hulls ({@code tools/GenerateAssets.java} recolour,
 * bankPose, hitFlash); a loop would need hundreds of baked frames a chassis, so here they are
 * derived once on decode instead. Same rules, with one change: only the green paint is rotated, so
 * the gold canopy and dark engines keep their colour on every livery.
 *
 * Seen from above, a bank is a horizontal squash about the spine. Seen in profile ({@code side}),
 * the same lean is the nose pitching up or down, so it is a small rotation instead.
 *
 * Plain ARGB arrays, no JavaFX, so the rules are testable without a toolkit.
 */
public final class HullPaint {

    /** The hue the hulls are generated in (militia green), and how far either side of it counts as paint. */
    static final float GREEN = 0.33f, BAND = 0.11f;
    private static final int HOSTILE_GLOW = 0xff6b5a;
    /** Degrees the profile pitches per step of lean: climbing and diving read without hiding the hull. */
    static final double PITCH_PER_LEAN = 7;

    private HullPaint() {
    }

    /**
     * A hull frame, painted, leaned to {@code pose} (0 hard left .. 4 hard right) and flashed.
     *
     * @param side the frame is the profile, nose right: the lean pitches it rather than banking it
     */
    public static int[] derive(int[] argb, int width, int height, float hue, float saturation,
                               int pose, boolean hit, boolean side) {
        int[] out = new int[argb.length];
        int lean = side ? 0 : pose - 2;
        double squash = 1 - 0.18 * Math.abs(lean);
        int cx = width / 2;
        float[] hsb = new float[3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // A roll seen from above is a horizontal squash about the spine.
                int sx = (int) Math.round(cx + (x - cx) / squash);
                if (sx < 0 || sx >= width) {
                    continue;
                }
                int p = argb[y * width + sx];
                int alpha = p >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                Color.RGBtoHSB(r, g, b, hsb);
                if (hsb[1] > 0.15f && Math.abs(hsb[0] - GREEN) < BAND) {
                    float h = ((hsb[0] - GREEN + hue) % 1 + 1) % 1;
                    int rgb = Color.HSBtoRGB(h, Math.min(1f, hsb[1] * saturation), hsb[2]);
                    r = (rgb >> 16) & 0xff; g = (rgb >> 8) & 0xff; b = rgb & 0xff;
                }
                // The wing dipping away from the light darkens toward its tip.
                int toward = lean > 0 ? cx - x : x - cx;
                if (lean != 0 && toward > 0) {
                    int permille = 1000 - 220 * Math.abs(lean) * toward / cx;
                    r = r * permille / 1000; g = g * permille / 1000; b = b * permille / 1000;
                }
                if (hit) {
                    r = mix(r, HOSTILE_GLOW >> 16); g = mix(g, (HOSTILE_GLOW >> 8) & 0xff); b = mix(b, HOSTILE_GLOW & 0xff);
                }
                out[y * width + x] = (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return side && pose != 2 ? pitch(out, width, height, (pose - 2) * PITCH_PER_LEAN) : out;
    }

    /**
     * Turns a profile frame about its centre, nearest-neighbour, on the same canvas.
     *
     * Positive is clockwise on screen, which for a nose-right hull dips the nose. Lean left is
     * toward the top of a side-on arena (the across axis runs up the screen), so it climbs.
     */
    static int[] pitch(int[] argb, int width, int height, double degrees) {
        int[] out = new int[argb.length];
        double r = Math.toRadians(degrees), cos = Math.cos(r), sin = Math.sin(r);
        double cx = width / 2.0, cy = height / 2.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Inverse map: where in the source does this destination pixel come from.
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                int sx = (int) Math.floor(cx + dx * cos + dy * sin);
                int sy = (int) Math.floor(cy - dx * sin + dy * cos);
                if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                    out[y * width + x] = argb[sy * width + sx];
                }
            }
        }
        return out;
    }

    private static int mix(int value, int target) {
        return Math.max(0, Math.min(255, (int) Math.round(value * 0.45 + target * 0.55)));
    }
}
