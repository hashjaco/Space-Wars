package com.hashimjacobs.spacecase.prefs;

/**
 * The gamepad buttons a player may bind an action to.
 *
 * Lives here rather than in the engine so that settings and their tests never touch the native
 * controller library; {@link com.hashimjacobs.spacecase.engine.Gamepad} is the only place these are
 * translated into SDL's own button enum.
 *
 * The face-button names follow the Xbox layout because SDL reports every pad in those terms -- on a
 * PlayStation pad {@code A} is cross and {@code B} is circle.
 */
public enum PadButton {

    A("A / Cross"),
    B("B / Circle"),
    X("X / Square"),
    Y("Y / Triangle"),
    LEFT_BUMPER("Left bumper"),
    RIGHT_BUMPER("Right bumper"),
    START("Start"),
    BACK("Select"),
    // Appended, never reordered: a stored binding is a name, and these arrived after the eight above.
    LEFT_TRIGGER("Left trigger"),
    RIGHT_TRIGGER("Right trigger"),
    LEFT_STICK("Left stick click"),
    RIGHT_STICK("Right stick click"),
    // The right stick pushed one way, read as a button. On an N64 pad these are the C-buttons,
    // which is how SDL reports them.
    C_UP("Right stick up"),
    C_DOWN("Right stick down"),
    C_LEFT("Right stick left"),
    C_RIGHT("Right stick right");

    private final String label;

    PadButton(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
