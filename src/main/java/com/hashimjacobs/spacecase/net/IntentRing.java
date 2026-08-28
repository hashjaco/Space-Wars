package com.hashimjacobs.spacecase.net;

import java.util.Arrays;

import com.hashimjacobs.spacecase.engine.Intent;

/**
 * One player's intents, by tick, in a fixed number of slots.
 *
 * A ring rather than a map so it cannot grow: a peer that keeps sending while this one is stalled
 * would otherwise bank intents until something ran out. Old entries are overwritten, which is the
 * right loss -- a tick that has already run is never asked for again.
 *
 * Each slot carries the tick it holds, so a slot that has wrapped answers null instead of handing
 * back an intent from {@link #SLOTS} ticks ago. Without that guard a stalled peer would look like a
 * peer flying in circles.
 */
final class IntentRing {

    /**
     * Four seconds at sixty ticks a second, against an input delay of three.
     *
     * The margin is for jitter, not for depth: a peer far enough behind to need more than this has
     * dropped by any useful definition, and {@link Lockstep} times them out long before the ring
     * would wrap.
     */
    private static final int SLOTS = 256;

    private final int[] tickAt = new int[SLOTS];
    private final Intent[] intents = new Intent[SLOTS];

    IntentRing() {
        // Tick zero is a real tick, so the empty marker cannot be zero.
        Arrays.fill(tickAt, -1);
    }

    void put(int tick, Intent intent) {
        int slot = slot(tick);
        tickAt[slot] = tick;
        intents[slot] = intent;
    }

    /** That tick's intent, or null if it has not arrived or has already been overwritten. */
    Intent get(int tick) {
        int slot = slot(tick);
        return tickAt[slot] == tick ? intents[slot] : null;
    }

    /**
     * Forgets everything, for a level boundary.
     *
     * The tick is re-based when a new fight starts, so without this a slot still holding tick 300
     * from the level just finished would answer a request for tick 300 of the one beginning.
     */
    void clear() {
        Arrays.fill(tickAt, -1);
        Arrays.fill(intents, null);
    }

    /** floorMod, not %, so a negative tick cannot index outside the array. */
    private static int slot(int tick) {
        return Math.floorMod(tick, SLOTS);
    }
}
