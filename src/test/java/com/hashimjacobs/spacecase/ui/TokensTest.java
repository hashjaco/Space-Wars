package com.hashimjacobs.spacecase.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two things worth pinning about the token layer, both of which fail loudly rather than subtly.
 *
 * Loading this class at all is the headless check. Every colour token is a static field, so the
 * first reference runs all of them; if a Font or any other toolkit type ever creeps into Tokens,
 * this test throws on a runner with no display and the whole suite goes with it -- which is the
 * point. That is a far better failure than discovering it in CI three commits later.
 *
 * Walking the fields by reflection rather than naming them is deliberate: a token added next month
 * is covered without anyone remembering to come back here.
 */
class TokensTest {

    private static List<Field> colourFields() {
        List<Field> found = new ArrayList<>();
        for (Field field : Tokens.class.getDeclaredFields()) {
            boolean constant = Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers());
            if (constant && field.getType() == Color.class) {
                found.add(field);
            }
        }
        return found;
    }

    @Test
    void everyColourTokenParses() throws ReflectiveOperationException {
        List<Field> fields = colourFields();
        assertFalse(fields.isEmpty(), "no colour tokens found, so this test is proving nothing");
        for (Field field : fields) {
            assertNotNull(field.get(null), field.getName() + " did not resolve to a colour");
        }
    }

    @Test
    void noColourTokenIsFullyTransparent() throws ReflectiveOperationException {
        // Color.web accepts a malformed-but-parseable string in more cases than you would like, and
        // an invisible token reads as "the text did not render" rather than as a palette mistake.
        for (Field field : colourFields()) {
            Color colour = (Color) field.get(null);
            assertTrue(colour.getOpacity() > 0, field.getName() + " is invisible");
        }
    }

    @Test
    void theTypeScaleRisesAndStaysReadable() {
        double[] scale = {Tokens.SIZE_CAPTION, Tokens.SIZE_SMALL, Tokens.SIZE_BODY,
                Tokens.SIZE_LABEL, Tokens.SIZE_ROW, Tokens.SIZE_BUTTON, Tokens.SIZE_HEADING,
                Tokens.SIZE_TITLE, Tokens.SIZE_OVERLAY_TITLE, Tokens.SIZE_DISPLAY};
        for (int i = 1; i < scale.length; i++) {
            assertTrue(scale[i] > scale[i - 1],
                    "the type scale has to rise, or the names stop meaning anything");
        }
        assertTrue(scale[0] >= 11, "nothing in the interface should be smaller than 11pt");
    }

    @Test
    void aVeilIsBlackAtTheOpacityAskedFor() {
        // Color keeps its channels as floats, so 0.72 comes back as 0.7200000286102295. Compare at
        // float precision rather than double, or this fails on a correct implementation.
        double tolerance = 1e-6;
        Color veil = Tokens.veil(0.72);
        assertEquals(0.72, veil.getOpacity(), tolerance);
        assertEquals(0, veil.getRed(), tolerance);
        assertEquals(0, veil.getGreen(), tolerance);
        assertEquals(0, veil.getBlue(), tolerance);
    }
}
