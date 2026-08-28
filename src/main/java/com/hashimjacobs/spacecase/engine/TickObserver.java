package com.hashimjacobs.spacecase.engine;

/**
 * Told what the world looked like after each tick of a fight.
 *
 * Exists for one caller: a networked game publishing its fingerprint so peers can catch a
 * divergence near where it started. Kept as a seam rather than a call into the netcode, because
 * {@code engine} has no business knowing what a peer is -- the same reason
 * {@link IntentSource} and {@link LevelHandshake} are interfaces here with no implementation
 * beside them.
 *
 * A local game leaves it unset and never computes a checksum at all.
 */
@FunctionalInterface
public interface TickObserver {

    /**
     * @param tick     the tick that has just finished, not the one about to run
     * @param checksum {@link World#checksum()} taken after that tick completed
     */
    void afterTick(int tick, long checksum);
}
