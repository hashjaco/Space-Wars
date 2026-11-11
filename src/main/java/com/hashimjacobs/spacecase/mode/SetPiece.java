package com.hashimjacobs.spacecase.mode;

/**
 * The one thing in a level that is not a wave: a timed stretch between two of them that asks for
 * something other than shooting a formation. Run by {@code engine.SpawnDirector}.
 *
 * {@code ticks} is the whole stretch including the warning, and is what par is charged.
 */
public enum SetPiece {

    NONE("", 0),
    /** Walls of rock too tough to shoot through, each with a gap. Fly it. */
    ASTEROID_FIELD("ASTEROID FIELD AHEAD", 900),
    /** A flood of fast, weak scouts. Let none past. */
    SWARM("SWARM INBOUND", 600),
    /** One heavy with two escorts. Ends early when all three are dead. */
    ACE("ACE INBOUND", 900),
    /** No enemies, four pickups. The breath. */
    SALVAGE("SALVAGE DRIFT", 480);

    private final String banner;
    private final int ticks;

    SetPiece(String banner, int ticks) {
        this.banner = banner;
        this.ticks = ticks;
    }

    public String banner() {
        return banner;
    }

    public int ticks() {
        return ticks;
    }
}
