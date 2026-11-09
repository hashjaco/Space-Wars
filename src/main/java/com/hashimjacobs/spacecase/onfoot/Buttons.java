package com.hashimjacobs.spacecase.onfoot;

import com.hashimjacobs.spacecase.engine.InputState;
import com.hashimjacobs.spacecase.engine.PlayerControls;
import com.hashimjacobs.spacecase.prefs.ControlAction;
import com.hashimjacobs.spacecase.prefs.Settings;

/** What one pilot is holding this tick. Edges are found by {@link Fighter}, which keeps last tick's. */
public record Buttons(boolean left, boolean right, boolean jump, boolean down,
                      boolean punch, boolean kick, boolean special, boolean guard, boolean jumpAlt) {

    /** One jump source, which is every caller but {@link #read}. */
    public Buttons(boolean left, boolean right, boolean jump, boolean down,
                   boolean punch, boolean kick, boolean special, boolean guard) {
        this(left, right, jump, down, punch, kick, special, guard, false);
    }

    public static final Buttons NONE = new Buttons(false, false, false, false, false, false, false, false);

    /**
     * Reads a seat's keys. Jump answers to up and to the ship's fire binding as well, which is what
     * puts it on a pad's A button (the default fire button) without a pad-side mode switch.
     *
     * The two are kept apart ({@code jump} is up, {@code jumpAlt} is fire) because Fighter jumps on a
     * press, not a hold: OR them into one and a stick pushed a touch upward while running -- which a
     * pad's deadzone turns into a held up -- makes A do nothing, since jump is already down.
     */
    public static Buttons read(InputState input, Settings settings, int player, boolean solo) {
        PlayerControls c = PlayerControls.of(settings, player, solo);
        return new Buttons(c.anyHeld(input, c.left()), c.anyHeld(input, c.right()),
                c.anyHeld(input, c.up()), c.anyHeld(input, c.down()),
                input.isHeld(settings.key(player, ControlAction.PUNCH)),
                input.isHeld(settings.key(player, ControlAction.KICK)),
                input.isHeld(settings.key(player, ControlAction.SPECIAL)),
                input.isHeld(settings.key(player, ControlAction.GUARD)),
                c.anyHeld(input, c.fire()));
    }
}
