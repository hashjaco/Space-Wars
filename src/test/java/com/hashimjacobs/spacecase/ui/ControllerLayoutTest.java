package com.hashimjacobs.spacecase.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.ui.ControllerLayout.Spot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller drawings as geometry. Whether a pad looks like itself is a question for the eye
 * (tools/preview ControllerSmoke); whether every button can be clicked and bound is one for here.
 */
class ControllerLayoutTest {

    @Test
    void everyButtonSitsInsideTheDrawing() {
        for (ControllerLayout layout : ControllerLayout.values()) {
            for (Spot spot : layout.spots()) {
                String which = layout + " " + spot.button();
                assertTrue(spot.x() - spot.width() / 2 >= 0, which);
                assertTrue(spot.x() + spot.width() / 2 <= ControllerLayout.WIDTH, which);
                assertTrue(spot.y() - spot.height() / 2 >= 0, which);
                assertTrue(spot.y() + spot.height() / 2 <= ControllerLayout.HEIGHT, which);
            }
        }
    }

    /** Two buttons sharing ground would make a click on one of them a coin toss. */
    @Test
    void noTwoButtonsOverlap() {
        for (ControllerLayout layout : ControllerLayout.values()) {
            List<Spot> spots = new ArrayList<>();
            layout.spots().forEach(spots::add);
            for (int a = 0; a < spots.size(); a++) {
                for (int b = a + 1; b < spots.size(); b++) {
                    assertFalse(spots.get(a).overlaps(spots.get(b)),
                            layout + ": " + spots.get(a).button() + " on " + spots.get(b).button());
                }
            }
        }
    }

    @Test
    void aClickOnAButtonFindsThatButton() {
        for (ControllerLayout layout : ControllerLayout.values()) {
            for (Spot spot : layout.spots()) {
                assertEquals(spot.button(), layout.hit(spot.x(), spot.y()), layout.toString());
            }
            assertNull(layout.hit(1, ControllerLayout.HEIGHT - 1), "the corner is empty space");
        }
    }

    /**
     * Reset must leave a pad playable: every action on a button this pad really has, and no two on
     * the same one. The N64 pad is why this is per layout -- it has no X or Y to punch and kick with.
     */
    @Test
    void everyLayoutsDefaultsUseOnlyButtonsItHas() {
        for (ControllerLayout layout : ControllerLayout.values()) {
            Set<PadButton> used = EnumSet.noneOf(PadButton.class);
            for (PadAction action : PadAction.values()) {
                PadButton button = layout.defaultButton(action);
                assertNotNull(layout.spot(button), layout + " has no " + button + " for " + action);
                assertTrue(used.add(button), layout + " puts two actions on " + button);
            }
        }
    }

    /** SDL follows the print on a Nintendo pad, so A is the right-hand button there, not the lower. */
    @Test
    void nintendoPadsCarryAOnTheRight() {
        Spot xboxA = ControllerLayout.XBOX.spot(PadButton.A);
        Spot xboxB = ControllerLayout.XBOX.spot(PadButton.B);
        assertTrue(xboxA.y() > xboxB.y(), "A is the bottom button on an Xbox pad");

        for (ControllerLayout layout : List.of(ControllerLayout.SWITCH_PRO, ControllerLayout.SNES)) {
            assertTrue(layout.spot(PadButton.A).x() > layout.spot(PadButton.B).x(), layout.toString());
            assertEquals("A", layout.spot(PadButton.A).label());
        }
    }

    @Test
    void aButtonIsCalledWhatThePadCallsIt() {
        assertEquals("L1", ControllerLayout.PS4.nameOf(PadButton.LEFT_BUMPER));
        assertEquals("Z", ControllerLayout.N64.nameOf(PadButton.LEFT_TRIGGER));
        assertEquals(PadButton.X.label(), ControllerLayout.N64.nameOf(PadButton.X),
                "a button the pad lacks keeps its general name");
    }

    /** Names as SDL gives them, several lifted from gamecontrollerdb.txt. */
    @Test
    void thePadsOwnNamePicksItsDrawing() {
        assertEquals(ControllerLayout.PS4, ControllerLayout.guess("PS4 Controller"));
        assertEquals(ControllerLayout.PS5, ControllerLayout.guess("DualSense Wireless Controller"));
        assertEquals(ControllerLayout.PS3, ControllerLayout.guess("PS3 Controller"));
        assertEquals(ControllerLayout.XBOX, ControllerLayout.guess("Xbox Series X Controller"));
        assertEquals(ControllerLayout.N64, ControllerLayout.guess("8BitDo N64"));
        assertEquals(ControllerLayout.N64, ControllerLayout.guess("Hyperkin N64 Adapter"));
        assertEquals(ControllerLayout.GAMECUBE, ControllerLayout.guess("8BitDo GameCube"));
        assertEquals(ControllerLayout.SNES, ControllerLayout.guess("8BitDo SN30 Pro"));
        assertEquals(ControllerLayout.SWITCH_PRO, ControllerLayout.guess("Nintendo Switch Pro Controller"));
        assertEquals(ControllerLayout.XBOX, ControllerLayout.guess("Some Pad Nobody Heard Of"));
        assertEquals(ControllerLayout.XBOX, ControllerLayout.guess(null), "no pad connected");
    }

    @Test
    void aStoredLayoutNameIsReadBackAndAnUnknownOneIsNotChosen() {
        for (ControllerLayout layout : ControllerLayout.values()) {
            assertEquals(layout, ControllerLayout.named(layout.name()));
        }
        assertNull(ControllerLayout.named(""));
        assertNull(ControllerLayout.named("DREAMCAST"));
    }
}
