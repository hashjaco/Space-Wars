package com.hashimjacobs.spacecase.mode;

/**
 * The goals every campaign level offers beyond clearing it, each paid out once, the first time.
 *
 * Kept as a bit per constant in {@code SaveGames}, so appending is safe and reordering is not.
 */
public enum Challenge {
    ALL_DOWN("Defeat every enemy", 150),
    CLOCK("Beat the par time", 150),
    CORE_1("Hidden core 1", 50),
    CORE_2("Hidden core 2", 50),
    CORE_3("Hidden core 3", 50),
    S_RANK("Earn an S rank", 150);

    /** Cleared plus every challenge: what 100% of one level is made of. */
    public static final int PARTS_PER_LEVEL = values().length + 1;
    public static final int ALL = (1 << values().length) - 1;

    private final String label;
    private final int credits;

    Challenge(String label, int credits) {
        this.label = label;
        this.credits = credits;
    }

    public String label() {
        return label;
    }

    public int credits() {
        return credits;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int bits) {
        return (bits & bit()) != 0;
    }

    /** The core challenge for core {@code index} (0-2). */
    public static Challenge core(int index) {
        return values()[CORE_1.ordinal() + index];
    }

    /** Credits owed for the challenges in {@code bits}. */
    public static int creditsFor(int bits) {
        int total = 0;
        for (Challenge challenge : values()) {
            if (challenge.in(bits)) {
                total += challenge.credits;
            }
        }
        return total;
    }
}
