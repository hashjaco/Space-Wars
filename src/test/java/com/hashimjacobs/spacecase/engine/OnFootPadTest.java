package com.hashimjacobs.spacecase.engine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.onfoot.Buttons;
import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.PadAction;
import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.prefs.ScratchSettings;
import com.hashimjacobs.spacecase.prefs.Settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole road from a pad to a pilot on foot: pad state, through {@link GamepadMapping}, into the
 * held keys, and out of {@link Buttons#read} -- the same calls the game makes each tick.
 */
class OnFootPadTest {

    private static final double DEADZONE = 0.30;

    /** What a pilot on foot reads after one poll of this pad state. */
    private static Buttons onFoot(Settings settings, int player, boolean solo,
                                  double x, double y, PadButton... buttons) {
        Set<PadButton> pressed = buttons.length == 0
                ? EnumSet.noneOf(PadButton.class) : EnumSet.copyOf(List.of(buttons));
        PadState state = new PadState(true, x, y, false, false, false, false, pressed);
        InputState input = new InputState();
        for (GamepadMapping.KeyChange change : new GamepadMapping(player - 1).poll(state, DEADZONE,
                action -> settings.padButton(player, action),
                action -> settings.key(player, action))) {
            // Escape never reaches the held set in the game either; it pauses.
            if (change.pressed() && change.code() != KeyCode.ESCAPE) {
                input.press(change.code());
            }
        }
        return Buttons.read(input, settings, player, solo);
    }

    private static void assertOnly(Buttons b, Predicate<Buttons> wanted, String what) {
        assertTrue(wanted.test(b), what + ": " + b);
        long down = List.of(b.left(), b.right(), b.jump(), b.down(), b.punch(), b.kick(),
                b.special(), b.guard(), b.jumpAlt()).stream().filter(held -> held).count();
        assertEquals(1, down, what + " and nothing else: " + b);
    }

    private static void everyControlReaches(Settings settings, int player, boolean solo) {
        String who = "P" + player + (solo ? " solo " : " ");
        assertOnly(onFoot(settings, player, solo, -1, 0), Buttons::left, who + "stick left walks left");
        assertOnly(onFoot(settings, player, solo, 1, 0), Buttons::right, who + "stick right walks right");
        assertOnly(onFoot(settings, player, solo, 0, -1), Buttons::jump, who + "stick up jumps");
        assertOnly(onFoot(settings, player, solo, 0, 1), Buttons::down, who + "stick down crouches");
        assertOnly(onFoot(settings, player, solo, 0, 0, settings.padButton(player, PadAction.FIRE)),
                Buttons::jumpAlt, who + "fire jumps");
        assertOnly(onFoot(settings, player, solo, 0, 0, settings.padButton(player, PadAction.PUNCH)),
                Buttons::punch, who + "punch");
        assertOnly(onFoot(settings, player, solo, 0, 0, settings.padButton(player, PadAction.KICK)),
                Buttons::kick, who + "kick");
        assertOnly(onFoot(settings, player, solo, 0, 0, settings.padButton(player, PadAction.SPECIAL)),
                Buttons::special, who + "special");
        assertOnly(onFoot(settings, player, solo, 0, 0, settings.padButton(player, PadAction.GUARD)),
                Buttons::guard, who + "guard");
        assertEquals(Buttons.NONE, onFoot(settings, player, solo, 0, 0,
                settings.padButton(player, PadAction.PAUSE)), who + "pause moves nobody");
    }

    @Test
    void aDefaultPadDrivesEveryOnFootControl() throws Exception {
        Settings settings = ScratchSettings.fresh();
        everyControlReaches(settings, 1, true);
        everyControlReaches(settings, 1, false);
        everyControlReaches(settings, 2, false);
    }

    @Test
    void reboundPadsAndKeyboardsStillDriveEveryOnFootControl() throws Exception {
        Settings settings = ScratchSettings.fresh();
        for (int player = 1; player <= 2; player++) {
            // Onto the buttons the controller screen added, and off every default key.
            settings.setPadButton(player, PadAction.FIRE, PadButton.RIGHT_TRIGGER);
            settings.setPadButton(player, PadAction.PUNCH, PadButton.LEFT_TRIGGER);
            settings.setPadButton(player, PadAction.KICK, PadButton.C_UP);
            settings.setPadButton(player, PadAction.SPECIAL, PadButton.LEFT_STICK);
            settings.setPadButton(player, PadAction.GUARD, PadButton.A);
        }
        KeyCode[] spare = {KeyCode.F1, KeyCode.F2, KeyCode.F3, KeyCode.F4, KeyCode.F5, KeyCode.F6,
                KeyCode.F7, KeyCode.F8, KeyCode.F9};
        KeyCode[] spareTwo = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4,
                KeyCode.DIGIT5, KeyCode.DIGIT6, KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9};
        for (ControlAction action : ControlAction.values()) {
            settings.setKey(1, action, spare[action.ordinal()]);
            settings.setKey(2, action, spareTwo[action.ordinal()]);
        }
        everyControlReaches(settings, 1, true);
        everyControlReaches(settings, 1, false);
        everyControlReaches(settings, 2, false);
    }
}
