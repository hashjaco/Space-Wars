package com.hashimjacobs.spacecase.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

/** One green loop becomes every paint, bank and cut: the paint stays on the paint, the rest follows the generator. */
class HullPaintTest {

    private static final int GREEN = 0xff000000 | Color.HSBtoRGB(0.33f, 0.8f, 0.7f);
    private static final int ENGINE = 0xff000000 | Color.HSBtoRGB(0.58f, 0.9f, 1f);
    private static final int CANOPY = 0xff000000 | Color.HSBtoRGB(0.13f, 0.8f, 0.9f);

    @Test
    void onlyTheGreenPaintTakesTheLivery() {
        int[] out = HullPaint.derive(new int[] {GREEN, ENGINE, CANOPY, 0}, 4, 1, 0.98f, 1f, 2, false, false);
        float[] hsb = Color.RGBtoHSB((out[0] >> 16) & 0xff, (out[0] >> 8) & 0xff, out[0] & 0xff, null);
        assertTrue(hsb[0] > 0.9f || hsb[0] < 0.05f, "green paint turns corsair red, hue " + hsb[0]);
        assertEquals(ENGINE, out[1], "the engines keep their blue");
        assertEquals(CANOPY, out[2], "the canopy keeps its gold");
        assertEquals(0, out[3], "clear stays clear");
    }

    @Test
    void militiaGreenIsTheFrameAsDrawn() {
        int[] in = {GREEN, ENGINE, CANOPY};
        int[] out = HullPaint.derive(in, 3, 1, HullPaint.GREEN, 1f, 2, false, false);
        for (int i = 0; i < in.length; i++) {
            assertEquals(in[i] & 0xfcfcfc, out[i] & 0xfcfcfc, "pixel " + i); // HSB round trip, within a bit
        }
    }

    @Test
    void aBankNarrowsTheHullAndAProfilePitchesInstead() {
        int w = 20, h = 4;
        int[] solid = new int[w * h];
        java.util.Arrays.fill(solid, GREEN);
        int[] banked = HullPaint.derive(solid, w, h, HullPaint.GREEN, 1f, 0, false, false);
        assertEquals(0, banked[0], "the hard bank pulls the wingtip in");
        assertNotEquals(0, banked[w / 2], "the spine stays put");

        int pixels = 0;
        int[] square = new int[40 * 40];
        for (int y = 15; y < 25; y++) {
            for (int x = 5; x < 35; x++) {
                square[y * 40 + x] = GREEN;
            }
        }
        for (int p : HullPaint.derive(square, 40, 40, HullPaint.GREEN, 1f, 0, false, true)) {
            pixels += p != 0 ? 1 : 0;
        }
        assertEquals(300, pixels, 30, "pitching turns the hull, it does not shrink or smear it");
    }

    @Test
    void theHitFlashLeansTowardTheHostileGlow() {
        int[] out = HullPaint.derive(new int[] {0xff000000}, 1, 1, HullPaint.GREEN, 1f, 2, true, false);
        assertEquals(0xff8c3b32, out[0]);
    }

    /**
     * The two conversions HullPaint carries are AWT's, bit for bit.
     *
     * AWT is fine in a desktop test and is the reference; the game itself must not touch it, because
     * Android has no AWT toolkit to load.
     */
    @Test
    void theColorConversionsMatchAwtExactly() {
        float[] ours = new float[3];
        float[] awt = new float[3];
        for (int r = 0; r < 256; r += 5) {
            for (int g = 0; g < 256; g += 3) {
                for (int b = 0; b < 256; b += 7) {
                    HullPaint.rgbToHsb(r, g, b, ours);
                    java.awt.Color.RGBtoHSB(r, g, b, awt);
                    org.junit.jupiter.api.Assertions.assertArrayEquals(awt, ours, 0f, r + "," + g + "," + b);
                    for (float shift = -1.5f; shift <= 1.5f; shift += 0.37f) {
                        float h = ours[0] + shift, sat = Math.min(1f, ours[1] * 1.3f);
                        org.junit.jupiter.api.Assertions.assertEquals(java.awt.Color.HSBtoRGB(h, sat, ours[2]),
                                HullPaint.hsbToRgb(h, sat, ours[2]), r + "," + g + "," + b + " shifted " + shift);
                    }
                }
            }
        }
    }
}
