package com.hashimjacobs.spacecase.onfoot;

/**
 * One attack, as fighting-game frame data: wind-up, the frames that can hit, then recovery.
 *
 * The hitbox is a rectangle in front of the fighter: {@code reach} wide starting at its front edge,
 * {@code top} to {@code bottom} as fractions of its height measured down from the head. Knockback is
 * in pixels a tick for a target of mass 1, flipped to the attacker's facing; negative y is up.
 *
 * {@code anim} names the Sprixen moveset key the frames come from (see tools/sprixen/onfoot.py).
 */
public enum Move {

    // The pilot. P,P,P is jab, cross, launcher; K,K is sweep, roundhouse; P,P,K ends in the heel.
    JAB("combo_1", 4, 3, 9, 58, 0.15, 0.45, 4, 2.5, 0, 14, 3, false),
    CROSS("combo_2", 6, 3, 11, 66, 0.15, 0.45, 6, 3.5, 0, 16, 4, false),
    UPPERCUT("combo_3", 7, 4, 17, 54, 0.0, 0.55, 9, 2.5, -13, 22, 6, false),
    SWEEP("dodge", 6, 4, 13, 74, 0.70, 1.0, 5, 1.5, -4, 18, 4, true),
    ROUNDHOUSE("flourish", 9, 4, 17, 80, 0.05, 0.50, 10, 11, -5, 24, 7, false),
    HEEL("flourish", 9, 5, 19, 84, 0.05, 0.55, 12, 13, -7, 26, 8, false),
    AIR_JAB("combo_1", 3, 5, 8, 54, 0.15, 0.55, 4, 2, -3, 14, 3, false),
    DIVE_KICK("dodge", 5, 40, 10, 50, 0.55, 1.05, 7, 6, -4, 18, 5, false),
    /** Down + Special: an instant thin ray that pierces. Cheap, quick, and the combo ender. */
    NEEDLE_RAY("special", 8, 6, 14, 0, 0, 0, 14, 4, -2, 24, 6, false),
    /** Released from a charge; the beam's size and damage come from how long it was held. */
    SOLAR_LANCE("special", 4, 0, 18, 0, 0, 0, 0, 0, 0, 20, 0, false),

    // A Foundry Thrall. Long wind-ups: a crowd has to be readable.
    CLAW("combo_1", 13, 4, 18, 70, 0.2, 0.6, 7, 4, -1, 16, 3, false),
    LUNGE("combo_2", 16, 8, 22, 66, 0.2, 0.6, 9, 6, -2, 18, 4, false),

    // The Foundry Foreman.
    PISTON("combo_1", 16, 5, 22, 120, 0.15, 0.55, 14, 10, -6, 24, 8, false),
    SLAM("combo_2", 20, 4, 30, 100, 0.1, 1.0, 18, 3, -9, 30, 9, true),
    POUND("combo_3", 26, 4, 30, 0, 0, 0, 0, 0, 0, 0, 10, false),
    MOLTEN_BEAM("special", 44, 0, 26, 0, 0, 0, 0, 0, 0, 20, 0, false),

    // A Riveteer. The rivet is a short, thin beam: a long wind-up says where it is going.
    RIVET("special", 30, 4, 26, 0, 0, 0, 12, 3, -1, 16, 4, false),

    // A Slag Brute. A heavy hook up close, and a shoulder charge from range that knocks down.
    SMASH("combo_1", 15, 4, 22, 82, 0.15, 0.6, 13, 7, -2, 20, 5, false),
    RAM("combo_2", 22, 16, 28, 64, 0.1, 0.95, 16, 12, -5, 24, 7, true),

    // The signatures, one per galaxy boss. Each is the boss's one new thing to learn.
    /** The Crucible Warden: a hammer blow that leaves slag pools burning on the floor. */
    SLAG_SLAM("special", 24, 4, 30, 90, 0.1, 1.0, 14, 4, -8, 26, 8, true),
    /** The Rime Matriarch: a mark blooms under each pilot, then spikes erupt from it. */
    ICE_SPIKES("special", 20, 2, 34, 0, 0, 0, 14, 3, -12, 24, 6, true),
    /** The Arc Tyrant: a line lights down the floor, then it dashes along it low enough to jump. */
    ARC_DASH("special", 40, 20, 26, 60, 0.55, 1.0, 16, 12, -6, 26, 7, true),
    /** The Null Revenant: drags the pilots in, then pounds; run against the pull, jump the shock. */
    GRAVITY_WELL("special", 20, 100, 30, 0, 0, 0, 0, 0, 0, 0, 10, false);

    final String anim;
    final int startup, active, recovery;
    final double reach, top, bottom;
    final int damage;
    final double knockX, knockY;
    final int hitstun, hitstop;
    /** Puts a grounded target on its back rather than staggering it. */
    final boolean knockdown;

    Move(String anim, int startup, int active, int recovery, double reach, double top, double bottom,
         int damage, double knockX, double knockY, int hitstun, int hitstop, boolean knockdown) {
        this.anim = anim;
        this.startup = startup;
        this.active = active;
        this.recovery = recovery;
        this.reach = reach;
        this.top = top;
        this.bottom = bottom;
        this.damage = damage;
        this.knockX = knockX;
        this.knockY = knockY;
        this.hitstun = hitstun;
        this.hitstop = hitstop;
        this.knockdown = knockdown;
    }

    public String anim() {
        return anim;
    }

    int length() {
        return startup + active + recovery;
    }

    boolean heavy() {
        return Math.abs(knockX) >= 9 || knockY <= -9 || knockdown;
    }

    /**
     * What a buffered press turns this move into once it has started hitting, or null for nothing.
     * The pilot's chains only; enemies never chain.
     */
    Move chain(boolean punch) {
        return switch (this) {
            case JAB -> punch ? CROSS : SWEEP;
            case CROSS -> punch ? UPPERCUT : HEEL;
            case SWEEP -> punch ? null : ROUNDHOUSE;
            default -> null;
        };
    }
}
