package com.hashimjacobs.spacecase.engine;

/**
 * Where one player's {@link Intent} for one tick comes from.
 *
 * Exists so the loop does not have to know whether a ship is being flown from this keyboard, a
 * touchscreen, or a socket. A networked game answers from the buffer of intents received for that
 * tick; {@link GameLoop} only ever asks.
 *
 * Asked by tick rather than for "the current input" on purpose: lockstep replays a tick number, and
 * an input sampled at render rate would answer differently depending on when the frame landed.
 */
@FunctionalInterface
public interface IntentSource {

    /**
     * @param tick the simulation tick being run, not the frame being drawn
     * @return that player's intent, never null; {@link Intent#NEUTRAL} for a peer that has dropped
     */
    Intent intentFor(int playerNumber, int tick);
}
