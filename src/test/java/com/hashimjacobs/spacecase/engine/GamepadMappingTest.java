package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.prefs.PadButton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gamepad rules, checked without a controller or the JavaFX toolkit. {@link KeyCode} is an enum
 * and needs neither.
 */
class GamepadMappingTest {

    private static final double DEADZONE = 0.30;
    private static final PadButton FIRE = PadButton.A;
    private static final PadButton PAUSE = PadButton.START;

    /** Matches GamepadMapping.REPEAT_EVERY_POLLS; polling this many times triggers one repeat. */
    private static final int REPEAT_EVERY_POLLS = 8;

    private final GamepadMapping playerOne = new GamepadMapping(0);

    private static PadState stick(double x, double y, PadButton... buttons) {
        Set<PadButton> pressed = buttons.length == 0
                ? EnumSet.noneOf(PadButton.class)
                : EnumSet.copyOf(List.of(buttons));
        PadState state = new PadState(true, x, y, false, false, false, false, pressed);
        return state;
    }

    private static PadState dpad(boolean up, boolean down, boolean left, boolean right) {
        PadState state = new PadState(true, 0, 0, up, down, left, right,
                EnumSet.noneOf(PadButton.class));
        return state;
    }

    private List<GamepadMapping.KeyChange> poll(GamepadMapping mapping, PadState state) {
        List<GamepadMapping.KeyChange> changes = mapping.poll(state, DEADZONE, FIRE, PAUSE);
        return changes;
    }

    private static List<KeyCode> pressedIn(List<GamepadMapping.KeyChange> changes) {
        List<KeyCode> codes = new ArrayList<>();
        for (GamepadMapping.KeyChange change : changes) {
            if (change.pressed()) {
                codes.add(change.code());
            }
        }
        return codes;
    }

    private static List<KeyCode> releasedIn(List<GamepadMapping.KeyChange> changes) {
        List<KeyCode> codes = new ArrayList<>();
        for (GamepadMapping.KeyChange change : changes) {
            if (!change.pressed()) {
                codes.add(change.code());
            }
        }
        return codes;
    }

    @Test
    void aStickPushedPastTheDeadzonePressesThatDirection() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, -0.9));
        assertEquals(List.of(KeyCode.W), pressedIn(changes));
    }

    @Test
    void theStickAxesFollowSdlWithPositiveYPointingDown() {
        assertEquals(List.of(KeyCode.S), pressedIn(poll(new GamepadMapping(0), stick(0, 0.9))));
        assertEquals(List.of(KeyCode.D), pressedIn(poll(new GamepadMapping(0), stick(0.9, 0))));
        assertEquals(List.of(KeyCode.A), pressedIn(poll(new GamepadMapping(0), stick(-0.9, 0))));
    }

    @Test
    void aStickInsideTheDeadzonePressesNothing() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0.2, -0.25));
        assertTrue(changes.isEmpty(), "a resting stick must not move the ship");
    }

    @Test
    void aDiagonalPressesBothDirections() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0.8, -0.8));
        assertEquals(Set.of(KeyCode.W, KeyCode.D), Set.copyOf(pressedIn(changes)));
    }

    @Test
    void aHeldDirectionSurvivesDriftBackTowardsTheDeadzone() {
        poll(playerOne, stick(0, -0.9));

        // Between the release threshold (0.6 of the deadzone) and the deadzone itself: without
        // hysteresis this reading would let go, and a resting stick would chatter every frame.
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, -0.25));

        assertTrue(changes.isEmpty(), "W stays held without being re-sent");
    }

    @Test
    void aDirectionIsReleasedOnceTheStickIsWellInsideTheDeadzone() {
        poll(playerOne, stick(0, -0.9));
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, -0.1));
        assertEquals(List.of(KeyCode.W), releasedIn(changes));
    }

    @Test
    void theDpadDrivesTheSameKeysAsTheStick() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, dpad(false, false, true, false));
        assertEquals(List.of(KeyCode.A), pressedIn(changes));
    }

    @Test
    void slotOneDrivesPlayerTwosKeys() {
        GamepadMapping playerTwo = new GamepadMapping(1);
        List<GamepadMapping.KeyChange> changes = poll(playerTwo, stick(0, -0.9, FIRE));

        assertEquals(Set.of(KeyCode.UP, KeyCode.COMMA, KeyCode.ENTER),
                Set.copyOf(pressedIn(changes)),
                "player two's own movement, fire and confirm keys, never player one's");
        assertEquals(List.of(KeyCode.ENTER), releasedIn(changes));
    }

    @Test
    void theFireButtonBothHoldsTheFireKeyAndTapsConfirm() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, FIRE));

        assertEquals(List.of(KeyCode.SHIFT, KeyCode.SPACE), pressedIn(changes));
        assertEquals(List.of(KeyCode.SPACE), releasedIn(changes),
                "confirm is tapped so it cannot stick in the held set once the menu has gone");
    }

    @Test
    void holdingFireDoesNotKeepConfirmingTheMenu() {
        poll(playerOne, stick(0, 0, FIRE));
        List<GamepadMapping.KeyChange> stillHeld = poll(playerOne, stick(0, 0, FIRE));

        assertFalse(pressedIn(stillHeld).contains(KeyCode.SPACE),
                "a held button would otherwise re-activate a menu item every poll");
    }

    @Test
    void theFireButtonConfirmsAgainAfterBeingLetGo() {
        poll(playerOne, stick(0, 0, FIRE));
        poll(playerOne, stick(0, 0));
        List<GamepadMapping.KeyChange> again = poll(playerOne, stick(0, 0, FIRE));

        assertTrue(pressedIn(again).contains(KeyCode.SPACE));
    }

    @Test
    void thePauseButtonIsTappedNotHeld() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, PAUSE));
        assertEquals(List.of(KeyCode.ESCAPE), pressedIn(changes));
        assertEquals(List.of(KeyCode.ESCAPE), releasedIn(changes));

        List<GamepadMapping.KeyChange> stillHeld = poll(playerOne, stick(0, 0, PAUSE));
        assertTrue(stillHeld.isEmpty(), "holding Start must not toggle pause every poll");
    }

    @Test
    void heldDirectionsAreResentPeriodically() {
        PadState pushed = stick(0, -0.9);
        poll(playerOne, pushed);

        List<KeyCode> repeats = new ArrayList<>();
        for (int i = 1; i < REPEAT_EVERY_POLLS; i++) {
            repeats.addAll(pressedIn(poll(playerOne, pushed)));
        }

        assertEquals(List.of(KeyCode.W), repeats,
                "exactly one repeat per cycle -- InputState clears its held set on unpause, so an "
                        + "edge-only pad would leave the ship frozen");
    }

    @Test
    void anUnpluggedPadLetsGoOfEverythingItWasHolding() {
        poll(playerOne, stick(0, -0.9, FIRE));

        List<GamepadMapping.KeyChange> changes = poll(playerOne, PadState.disconnected());

        assertEquals(Set.of(KeyCode.W, KeyCode.SHIFT), Set.copyOf(releasedIn(changes)),
                "a pad whose battery dies must not leave the ship drifting");
        assertTrue(pressedIn(changes).isEmpty());
    }

    @Test
    void reconnectingAfterAnUnplugPressesAfresh() {
        poll(playerOne, stick(0, -0.9));
        poll(playerOne, PadState.disconnected());

        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, -0.9));

        assertEquals(List.of(KeyCode.W), pressedIn(changes));
    }
}
