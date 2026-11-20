package com.hashimjacobs.spacecase.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

/** The baked effects do what the live effects did: same shape, same alpha, the spill inside the pad. */
class BakedTest {

    private static final int ORANGE = 0xffff8a2a;

    @Test
    void aShadowIsTheShapeInBlack() {
        int[] out = Baked.silhouette(new int[] {ORANGE, 0x80123456, 0});
        assertEquals(0xff000000, out[0]);
        assertEquals(0x80000000, out[1], "the edge keeps its softness");
        assertEquals(0, out[2]);
    }

    @Test
    void aGlowBrightensTheBodyAndSpillsOnlyIntoThePad() {
        int w = 8, h = 8, pad = Baked.GLOW_PAD;
        int[] solid = new int[w * h];
        java.util.Arrays.fill(solid, 0xff604020);
        int[] out = Baked.glow(solid, w, h, pad, 0.35f);
        int pw = w + 2 * pad;
        assertEquals((w + 2 * pad) * (h + 2 * pad), out.length);
        assertEquals(0, out[0], "the far corner stays clear");
        int centre = out[(pad + h / 2) * pw + pad + w / 2];
        assertEquals(0xff, centre >>> 24);
        assertTrue(((centre >> 16) & 0xff) > 0x60, "the body is lit, red " + ((centre >> 16) & 0xff));
        int beside = out[(pad + h / 2) * pw + pad - 2];
        assertTrue(beside >>> 24 > 0, "the glow spills past the edge");
        assertTrue(beside >>> 24 < 0xff, "and fades as it goes");
    }

    @Test
    void aGlowLeavesTheEmptyCanvasClear() {
        // A real hull's shape: a band of mixed colours over clear rows. Float dust in the blur once
        // came out as opaque streaks below it.
        int w = 60, h = 40;
        int[] hull = new int[w * h];
        java.util.Random random = new java.util.Random(7);
        for (int y = 8; y < 18; y++) {
            for (int x = 4; x < 56; x++) {
                hull[y * w + x] = 0xff000000 | random.nextInt(0x1000000);
            }
        }
        int pad = Baked.GLOW_PAD;
        int[] out = Baked.glow(hull, w, h, pad, 0.35f);
        int pw = w + 2 * pad;
        for (int y = pad + 18 + pad; y < h + 2 * pad; y++) {
            for (int x = 0; x < pw; x++) {
                assertEquals(0, out[y * pw + x] >>> 24, "clear at " + x + "," + y);
            }
        }
    }

    @Test
    void aDiscIsSolidInTheMiddleClearInTheCornersAndSoftAtTheRim() {
        int[] out = Baked.disc(0xffff8a2a, 16);
        assertEquals(0xffff8a2a, out[8 * 16 + 8]);
        assertEquals(0, out[0], "corner");
        int rim = out[8 * 16];
        assertTrue(rim >>> 24 > 0 && rim >>> 24 < 0xff, "the rim pixel is part-covered, alpha " + (rim >>> 24));
    }

    @Test
    void aHueShiftTurnsTheColourAndKeepsAlphaAndBrightness() {
        int in = 0x80000000 | (Color.HSBtoRGB(0.1f, 0.8f, 0.7f) & 0xffffff);
        int out = Baked.hueRotate(new int[] {in}, 0.4f, 0)[0];
        assertEquals(0x80, out >>> 24);
        float[] hsb = Color.RGBtoHSB((out >> 16) & 0xff, (out >> 8) & 0xff, out & 0xff, null);
        assertEquals(0.3f, hsb[0], 0.01f, "half the ColorAdjust hue, a fifth of a turn");
        assertEquals(0.8f, hsb[1], 0.01f);
        assertEquals(0.7f, hsb[2], 0.01f);
    }

    @Test
    void screenLikeAddsWhatScreenAddsOnTheBrightestChannel() {
        int[] out = Baked.screenLike(new int[] {0xc0ff8040, 0xff000000});
        assertEquals(0xc0, out[0] >>> 24, "a full-red pixel keeps its alpha");
        assertEquals(0xffff8040, out[0] | 0xff000000);
        assertEquals(0, out[1], "black adds nothing under SCREEN, so it is clear");
    }
}
