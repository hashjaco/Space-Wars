package com.hashimjacobs.spacecase.asset;

import java.awt.Color;

/**
 * Everything a CG hull is flown in, from the one straight, green loop on disk: the paint, the bank,
 * the hit flash and the side-on turn. The generator bakes these for the drawn hulls
 * ({@code tools/GenerateAssets.java} recolour, bankPose, hitFlash, quarterTurnLeft); a loop would
 * need 960 baked frames a chassis, so here they are derived once on decode instead. Same rules,
 * with one change: only the green paint is rotated, so the gold canopy and blue engines keep their
 * colour on every livery.
 *
 * Plain ARGB arrays, no JavaFX, so the rules are testable without a toolkit.
 */
public final class HullPaint {

    /** The hue the hulls are generated in (militia green), and how far either side of it counts as paint. */
    static final float GREEN = 0.33f, BAND = 0.11f;
    private static final int HOSTILE_GLOW = 0xff6b5a;

    private HullPaint() {
    }

    /** A hull frame, painted, banked to {@code pose} (0 hard left .. 4 hard right), flashed and turned. */
    public static int[] derive(int[] argb, int width, int height, float hue, float saturation,
                               int pose, boolean hit, boolean side) {
        int[] out = new int[argb.length];
        int lean = pose - 2;
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
        return side ? quarterTurn(out, width, height) : out;
    }

    /** Nose-up to nose-right, the generator's quarterTurnLeft: the result is height wide, width tall. */
    static int[] quarterTurn(int[] argb, int width, int height) {
        int[] out = new int[argb.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[x * height + (height - 1 - y)] = argb[y * width + x];
            }
        }
        return out;
    }

    private static int mix(int value, int target) {
        return Math.max(0, Math.min(255, (int) Math.round(value * 0.45 + target * 0.55)));
    }
}
