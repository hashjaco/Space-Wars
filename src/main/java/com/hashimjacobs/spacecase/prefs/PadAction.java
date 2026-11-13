package com.hashimjacobs.spacecase.prefs;

/**
 * Something a pilot can do with a pad button, and the button that does it by default.
 *
 * The pad's half of {@link ControlAction}. Movement is absent because it is not a button: it lives
 * on the stick and the d-pad and is not rebindable. Pause is here and not there because the keyboard
 * pauses with Escape, which is not rebindable either.
 *
 * The defaults are the buttons these actions were fixed to before the controller screen existed.
 */
public enum PadAction {

    FIRE("Fire", PadButton.A, ControlAction.FIRE),
    PAUSE("Pause", PadButton.START, null),
    SPECIAL("Special", PadButton.B, ControlAction.SPECIAL),
    // On foot only, like the keys they stand in for.
    PUNCH("Punch", PadButton.X, ControlAction.PUNCH),
    KICK("Kick", PadButton.Y, ControlAction.KICK),
    GUARD("Guard", PadButton.RIGHT_BUMPER, ControlAction.GUARD);

    private final String label;
    private final PadButton defaultButton;
    private final ControlAction key;

    PadAction(String label, PadButton defaultButton, ControlAction key) {
        this.label = label;
        this.defaultButton = defaultButton;
        this.key = key;
    }

    public String label() {
        return label;
    }

    public PadButton defaultButton() {
        return defaultButton;
    }

    /** The keyboard action whose key this button holds down, or null for pause, which has none. */
    public ControlAction key() {
        return key;
    }
}
