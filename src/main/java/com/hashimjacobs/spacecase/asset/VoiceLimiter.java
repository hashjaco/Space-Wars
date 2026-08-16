package com.hashimjacobs.spacecase.asset;

/**
 * A ceiling on how many sound effects may be sounding at once.
 *
 * Every JavaFX {@code AudioClip.play()} builds a whole native media player, with a thread of its
 * own, and tears it down when the voice finishes. Enough of those alive at once and the teardown
 * collides with the media pulse the soundtrack runs on the JavaFX thread, which hangs the window
 * -- a frozen game because too many lasers were in the air. Shorter samples cut the count, since
 * voices alive is only ever duration times fire rate, but a ceiling is what keeps it bounded when
 * a future weapon fires faster or a new effect is authored long.
 *
 * Refuses rather than queues: a shot that has already been fired is not worth hearing late, and a
 * queue would hold exactly the native players this exists to limit.
 *
 * Not thread-safe. Called only from the audio thread.
 */
final class VoiceLimiter {

    /**
     * Voices allowed at once across everything.
     *
     * The dump from the freeze showed about twenty native players alive. Driven at the game's own
     * trigger rates, this ceiling plus the per-effect one below holds the peak at eleven, against
     * nineteen measured with no ceiling at all.
     *
     * Note the count cannot be read off the sample lengths alone: JavaFX imposes a voice ceiling
     * of its own, so shortening a sample buys fewer simultaneous voices but not fewer native
     * players created -- one of those is built and torn down per play() however short the sound
     * is. Refusing a play is the only thing that reduces the churn.
     */
    static final int MAX_VOICES = 8;

    /**
     * Voices allowed at once from any single effect.
     *
     * Without it the loudest repeater takes the whole budget: the boss gun is a 1.6-second
     * sustained burst retriggered up to five times a second, which alone wants seven or eight
     * voices, and a bare global ceiling would let it silence the player's own gun for the length
     * of a boss fight. One number applied to every effect rather than a tuned table -- three
     * overlapping copies is enough for any of these to sound full.
     */
    static final int MAX_VOICES_PER_EFFECT = 3;

    /** When each slot's voice finishes, in nanoTime. A free slot is one already in the past. */
    private final long[] freeAt;

    VoiceLimiter() {
        this(MAX_VOICES);
    }

    VoiceLimiter(int maxVoices) {
        this.freeAt = new long[maxVoices];
    }

    /**
     * Whether a slot is free, without taking one.
     *
     * Lets a caller check the global ceiling before committing a per-effect slot, so a sound that
     * is going to be dropped anyway does not consume the budget of the effect it belongs to.
     */
    boolean hasFree(long nowNanos) {
        for (long slotFreeAt : freeAt) {
            if (slotFreeAt - nowNanos <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes a voice slot for a sound of {@code durationNanos}, or reports that the ceiling is
     * reached and the sound should be dropped.
     */
    boolean claim(long nowNanos, long durationNanos) {
        for (int slot = 0; slot < freeAt.length; slot++) {
            // Subtraction rather than a plain compare, so this still holds when nanoTime wraps.
            if (freeAt[slot] - nowNanos <= 0) {
                freeAt[slot] = nowNanos + durationNanos;
                return true;
            }
        }
        return false;
    }

    /** How many voices are still sounding at {@code nowNanos}. For tests and diagnostics. */
    int live(long nowNanos) {
        int count = 0;
        for (long slotFreeAt : freeAt) {
            if (slotFreeAt - nowNanos > 0) {
                count++;
            }
        }
        return count;
    }
}
