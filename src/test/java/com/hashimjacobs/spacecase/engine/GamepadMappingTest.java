package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** What the stick and d-pad send: codes of the pad's own, not anybody's keyboard keys. */
    private static final KeyCode PAD_UP = GamepadMapping.direction(1, ControlAction.UP);
    private static final KeyCode PAD_DOWN = GamepadMapping.direction(1, ControlAction.DOWN);
    private static final KeyCode PAD_LEFT = GamepadMapping.direction(1, ControlAction.LEFT);
    private static final KeyCode PAD_RIGHT = GamepadMapping.direction(1, ControlAction.RIGHT);
    private static final KeyCode PAD_TWO_UP = GamepadMapping.direction(2, ControlAction.UP);

    /** A pad nobody has rebound, held by a player who has not rebound their keyboard either. */
    private List<GamepadMapping.KeyChange> poll(GamepadMapping mapping, PadState state) {
        return poll(mapping, state, 1);
    }

    private List<GamepadMapping.KeyChange> poll(GamepadMapping mapping, PadState state, int player) {
        return mapping.poll(state, DEADZONE, PadAction::defaultButton, keysOf(player));
    }

    private static Function<ControlAction, KeyCode> keysOf(int player) {
        return action -> action.defaultKey(player);
    }

    /** The default buttons with one action moved, and whatever was there moved out of its way. */
    private static Function<PadAction, PadButton> moved(PadAction action, PadButton button) {
        return asked -> asked == action ? button
                : asked.defaultButton() == button ? action.defaultButton()
                : asked.defaultButton();
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
        assertEquals(List.of(PAD_UP), pressedIn(changes));
    }

    @Test
    void theStickAxesFollowSdlWithPositiveYPointingDown() {
        assertEquals(List.of(PAD_DOWN), pressedIn(poll(new GamepadMapping(0), stick(0, 0.9))));
        assertEquals(List.of(PAD_RIGHT), pressedIn(poll(new GamepadMapping(0), stick(0.9, 0))));
        assertEquals(List.of(PAD_LEFT), pressedIn(poll(new GamepadMapping(0), stick(-0.9, 0))));
    }

    @Test
    void aStickInsideTheDeadzonePressesNothing() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0.2, -0.25));
        assertTrue(changes.isEmpty(), "a resting stick must not move the ship");
    }

    @Test
    void aDiagonalPressesBothDirections() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0.8, -0.8));
        assertEquals(Set.of(PAD_UP, PAD_RIGHT), Set.copyOf(pressedIn(changes)));
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
        assertEquals(List.of(PAD_UP), releasedIn(changes));
    }

    @Test
    void theDpadDrivesTheSameKeysAsTheStick() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, dpad(false, false, true, false));
        assertEquals(List.of(PAD_LEFT), pressedIn(changes));
    }

    @Test
    void slotOneDrivesPlayerTwosKeys() {
        GamepadMapping playerTwo = new GamepadMapping(1);
        List<GamepadMapping.KeyChange> changes = poll(playerTwo, stick(0, -0.9, FIRE), 2);

        assertEquals(Set.of(PAD_TWO_UP, KeyCode.COMMA, KeyCode.ENTER),
                Set.copyOf(pressedIn(changes)),
                "player two's own movement, fire and confirm keys, never player one's");
        assertEquals(List.of(KeyCode.ENTER), releasedIn(changes));
    }

    @Test
    void theFireButtonBothHoldsTheFireKeyAndTapsConfirm() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, FIRE));

        assertEquals(List.of(KeyCode.SHIFT, KeyCode.ENTER), pressedIn(changes));
        assertEquals(List.of(KeyCode.ENTER), releasedIn(changes),
                "confirm is tapped so it cannot stick in the held set once the menu has gone");
    }

    @Test
    void holdingFireDoesNotKeepConfirmingTheMenu() {
        poll(playerOne, stick(0, 0, FIRE));
        List<GamepadMapping.KeyChange> stillHeld = poll(playerOne, stick(0, 0, FIRE));

        assertFalse(pressedIn(stillHeld).contains(KeyCode.ENTER),
                "a held button would otherwise re-activate a menu item every poll");
    }

    @Test
    void theFireButtonConfirmsAgainAfterBeingLetGo() {
        poll(playerOne, stick(0, 0, FIRE));
        poll(playerOne, stick(0, 0));
        List<GamepadMapping.KeyChange> again = poll(playerOne, stick(0, 0, FIRE));

        assertTrue(pressedIn(again).contains(KeyCode.ENTER));
    }

    @Test
    void thePauseButtonIsTappedNotHeld() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, PAUSE));
        assertEquals(List.of(KeyCode.ESCAPE), pressedIn(changes));
        assertEquals(List.of(KeyCode.ESCAPE), releasedIn(changes));

        List<GamepadMapping.KeyChange> stillHeld = poll(playerOne, stick(0, 0, PAUSE));
        assertTrue(stillHeld.isEmpty(), "holding Start must not toggle pause every poll");
    }

    /** The face buttons and right bumper hold each seat's on-foot keys: punch, kick, guard. */
    @Test
    void faceButtonsHoldTheOnFootKeys() {
        assertEquals(List.of(KeyCode.J), pressedIn(poll(playerOne, stick(0, 0, PadButton.X))));
        assertEquals(List.of(KeyCode.K), pressedIn(poll(new GamepadMapping(0), stick(0, 0, PadButton.Y))));
        assertEquals(List.of(KeyCode.I), pressedIn(poll(new GamepadMapping(0), stick(0, 0, PadButton.RIGHT_BUMPER))));
        assertEquals(List.of(KeyCode.SLASH), pressedIn(poll(new GamepadMapping(1), stick(0, 0, PadButton.X), 2)));
    }

    /**
     * B backs out of a menu, which is the convention every console has taught.
     *
     * Tapped rather than held, like the other two one-shot buttons: a menu reacts to the press, and
     * the release keeps the code from sticking in the held set once the menu has gone.
     */
    @Test
    void theBButtonIsTappedAndMeansCancel() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, PadButton.B));
        // Plus the on-foot special key, held: B is special when the pilots are on foot.
        assertEquals(List.of(KeyCode.L, GamepadMapping.MENU_CANCEL), pressedIn(changes));
        assertEquals(List.of(GamepadMapping.MENU_CANCEL), releasedIn(changes));

        List<GamepadMapping.KeyChange> stillHeld = poll(playerOne, stick(0, 0, PadButton.B));
        assertTrue(stillHeld.isEmpty(), "holding B must not back out once per poll");
    }

    /**
     * And it must not pause the game.
     *
     * The whole reason cancel is its own code rather than Escape. B sits next to fire and gets
     * mashed; if it spoke Escape it would pause a fight, because that is what gameplay hears.
     */
    @Test
    void theBButtonNeverSpeaksEscape() {
        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, 0, PadButton.B));

        assertFalse(pressedIn(changes).contains(KeyCode.ESCAPE),
                "B would pause the game mid-fight");
        assertNotEquals(KeyCode.ESCAPE, GamepadMapping.MENU_CANCEL,
                "cancel and pause have to stay separate codes for that to hold");
    }

    /** An explicit binding wins, so a player who put fire on B gets fire and not both. */
    @Test
    void anExplicitBindingOnBBeatsTheCancelConvention() {
        GamepadMapping fireOnB = new GamepadMapping(0);
        List<GamepadMapping.KeyChange> firing =
                fireOnB.poll(stick(0, 0, PadButton.B), DEADZONE, moved(PadAction.FIRE, PadButton.B), keysOf(1));
        assertTrue(pressedIn(firing).contains(KeyCode.ENTER), "B should still confirm");
        assertFalse(pressedIn(firing).contains(GamepadMapping.MENU_CANCEL),
                "B cannot confirm and cancel at the same time");

        GamepadMapping pauseOnB = new GamepadMapping(0);
        List<GamepadMapping.KeyChange> pausing =
                pauseOnB.poll(stick(0, 0, PadButton.B), DEADZONE, moved(PadAction.PAUSE, PadButton.B), keysOf(1));
        assertTrue(pressedIn(pausing).contains(KeyCode.ESCAPE), "B should still pause");
        assertFalse(pressedIn(pausing).contains(GamepadMapping.MENU_CANCEL),
                "B cannot pause and cancel at the same time");
    }

    @Test
    void heldDirectionsAreResentPeriodically() {
        PadState pushed = stick(0, -0.9);
        poll(playerOne, pushed);

        List<KeyCode> repeats = new ArrayList<>();
        for (int i = 1; i < REPEAT_EVERY_POLLS; i++) {
            repeats.addAll(pressedIn(poll(playerOne, pushed)));
        }

        assertEquals(List.of(PAD_UP), repeats,
                "exactly one repeat per cycle -- InputState clears its held set on unpause, so an "
                        + "edge-only pad would leave the ship frozen");
    }

    @Test
    void anUnpluggedPadLetsGoOfEverythingItWasHolding() {
        poll(playerOne, stick(0, -0.9, FIRE));

        List<GamepadMapping.KeyChange> changes = poll(playerOne, PadState.disconnected());

        assertEquals(Set.of(PAD_UP, KeyCode.SHIFT), Set.copyOf(releasedIn(changes)),
                "a pad whose battery dies must not leave the ship drifting");
        assertTrue(pressedIn(changes).isEmpty());
    }

    @Test
    void reconnectingAfterAnUnplugPressesAfresh() {
        poll(playerOne, stick(0, -0.9));
        poll(playerOne, PadState.disconnected());

        List<GamepadMapping.KeyChange> changes = poll(playerOne, stick(0, -0.9));

        assertEquals(List.of(PAD_UP), pressedIn(changes));
    }

    /**
     * A button holds the key its player has bound, not the key the action shipped on.
     *
     * The pad used to press each action's default key, so a pilot who moved fire off Shift at the
     * keyboard found the pad's fire button dead.
     */
    @Test
    void aButtonHoldsThePlayersOwnKeyAfterAKeyboardRebind() {
        Function<ControlAction, KeyCode> rebound =
                action -> action == ControlAction.FIRE ? KeyCode.F : action.defaultKey(1);

        List<GamepadMapping.KeyChange> changes =
                playerOne.poll(stick(0, 0, FIRE), DEADZONE, PadAction::defaultButton, rebound);

        assertTrue(pressedIn(changes).contains(KeyCode.F));
        assertFalse(pressedIn(changes).contains(KeyCode.SHIFT));
    }

    /** Nor do the stick and d-pad depend on the keyboard: they have codes no key can be bound to. */
    @Test
    void directionsAreThePadsOwnCodesAndReadAsArrowsInAMenu() {
        for (ControlAction way : List.of(ControlAction.UP, ControlAction.DOWN,
                ControlAction.LEFT, ControlAction.RIGHT)) {
            assertNotEquals(GamepadMapping.direction(1, way), GamepadMapping.direction(2, way),
                    "the garage tells the two seats apart by these");
            for (ControlAction any : ControlAction.values()) {
                assertNotEquals(any.defaultKey(1), GamepadMapping.direction(1, way));
                assertNotEquals(any.defaultKey(2), GamepadMapping.direction(2, way));
            }
        }
        assertEquals(KeyCode.UP, GamepadMapping.menuKey(PAD_UP));
        assertEquals(KeyCode.LEFT, GamepadMapping.menuKey(GamepadMapping.direction(2, ControlAction.LEFT)));
        assertEquals(KeyCode.W, GamepadMapping.menuKey(KeyCode.W), "a real key is left alone");
        assertNull(GamepadMapping.direction(1, ControlAction.FIRE), "fire is not a direction");
    }

    /** Any action can go on any button, the triggers and the right stick's directions included. */
    @Test
    void anActionMovedToATriggerOrAStickDirectionFollowsIt() {
        List<GamepadMapping.KeyChange> onTrigger = playerOne.poll(
                stick(0, 0, PadButton.RIGHT_TRIGGER), DEADZONE,
                moved(PadAction.FIRE, PadButton.RIGHT_TRIGGER), keysOf(1));
        assertEquals(List.of(KeyCode.SHIFT, KeyCode.ENTER), pressedIn(onTrigger));

        List<GamepadMapping.KeyChange> onStick = new GamepadMapping(0).poll(
                stick(0, 0, PadButton.C_LEFT), DEADZONE,
                moved(PadAction.PUNCH, PadButton.C_LEFT), keysOf(1));
        assertEquals(List.of(KeyCode.J), pressedIn(onStick));

        List<GamepadMapping.KeyChange> oldButton = new GamepadMapping(0).poll(
                stick(0, 0, PadButton.X), DEADZONE,
                moved(PadAction.PUNCH, PadButton.C_LEFT), keysOf(1));
        assertTrue(oldButton.isEmpty(), "X no longer punches once punch has moved off it");
    }

    /**
     * While the controller screen waits for a button, the pad reports the button and nothing else.
     *
     * The button that armed the row is still down when listening starts, so it must not count; and
     * the button that gets bound must not confirm, cancel or pause on its way past, then or after.
     */
    @Test
    void captureReportsOnlyANewlyPressedButtonAndSendsNoKeys() {
        poll(playerOne, stick(0, -0.9, FIRE));
        assertEquals(Set.of(PAD_UP, KeyCode.SHIFT), Set.copyOf(releasedIn(playerOne.releaseKeys())),
                "listening starts by letting go of what the pad was holding");

        assertNull(playerOne.capture(stick(0, 0, FIRE)), "the button that armed the row is old news");
        assertEquals(PadButton.START, playerOne.capture(stick(0, 0, FIRE, PadButton.START)));
        assertNull(playerOne.capture(stick(0, 0, PadButton.START)), "reported once, not every poll");

        List<GamepadMapping.KeyChange> after = poll(playerOne, stick(0, 0, PadButton.START));
        assertTrue(after.isEmpty(), "the bound button, still held, must not pause as play resumes");
    }

    /**
     * One press of fire must reach the garage as one buy.
     *
     * Player one's confirm tap used to be Space, and Space is also the extra fire key a pilot
     * flying alone has -- so in the garage, where fire buys, the held fire key bought a level and
     * the confirm tap bought another on the same press.
     */
    @Test
    void theConfirmTapIsNeverAlsoAFireKey() {
        for (int player = 1; player <= 2; player++) {
            List<KeyCode> pressed = pressedIn(poll(new GamepadMapping(player - 1),
                    stick(0, 0, FIRE), player));
            assertEquals(2, pressed.size(), "the fire key and the confirm tap");
            for (boolean solo : List.of(true, false)) {
                assertFalse(pressed.contains(ControlAction.FIRE.alternate(player, solo)),
                        "player " + player + " would buy twice per press in the garage");
            }
        }
    }
}
