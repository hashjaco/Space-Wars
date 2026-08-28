package com.hashimjacobs.spacecase.engine;

/**
 * The agreement every machine must reach before the next fight begins.
 *
 * Sits between two levels and nowhere else. The fight itself is held in step tick by tick, by
 * {@link GameLoop#setStepGate}; this is the other half, and it exists because the four phases
 * between two fights deliberately are <em>not</em> held in step. The victory lap, the debrief, the
 * garage and the warp each end when something purely local happens -- a pilot pressing a key, a
 * pilot finishing their shopping, this machine's disk finishing a decode -- and lockstepping them
 * would put the slowest disk in the match in charge of everyone's pace.
 *
 * The price of letting them run free is that machines arrive here disagreeing about two things that
 * the simulation reads: the tick, which drives enemy fire patterns and scrolls the terrain, and the
 * loadouts, which the garage has just changed. Implementations settle both.
 *
 * Kept as an interface with no implementation in this package on purpose: {@code engine} has no
 * business knowing what a socket is, and the local game passes null.
 */
@FunctionalInterface
public interface LevelHandshake {

    /**
     * Called on every warp tick once this machine's own warp is finished, until it answers true.
     *
     * Answering false leaves the loop in {@code WARP}, still scrolling and still drawing, which is
     * what makes waiting for a slow peer look like a long warp rather than a hung game.
     *
     * An implementation that answers true must already have put {@code world} in the agreed state:
     * the tick realigned through {@link World#resumeAt}, and every pilot's loadout applied.
     *
     * @param level the level about to be entered, so a peer that raced ahead cannot free a barrier
     *              the others are still standing at
     */
    boolean readyToFight(int level, World world);
}
