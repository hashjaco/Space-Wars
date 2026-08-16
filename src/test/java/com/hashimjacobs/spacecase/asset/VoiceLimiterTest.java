package com.hashimjacobs.spacecase.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ceiling that keeps native media players from piling up. Time is passed in rather than read,
 * so these run instantly and without the JavaFX toolkit.
 */
class VoiceLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsVoicesUpToTheCeilingAndRefusesTheNext() {
        VoiceLimiter limiter = new VoiceLimiter(3);

        assertTrue(limiter.claim(0, SECOND));
        assertTrue(limiter.claim(0, SECOND));
        assertTrue(limiter.claim(0, SECOND));
        assertFalse(limiter.claim(0, SECOND), "the fourth voice is over the ceiling");
        assertEquals(3, limiter.live(0));
    }

    @Test
    void reclaimsSlotsAsVoicesFinish() {
        VoiceLimiter limiter = new VoiceLimiter(2);
        limiter.claim(0, SECOND);
        limiter.claim(0, 3 * SECOND);

        assertFalse(limiter.claim(SECOND / 2, SECOND), "both are still sounding");

        // The one-second voice has ended; the three-second one has not.
        assertTrue(limiter.claim(2 * SECOND, SECOND));
        assertEquals(2, limiter.live(2 * SECOND));
        assertEquals(0, limiter.live(10 * SECOND), "everything has finished by now");
    }

    /** Sustained fire has to keep sounding, not lock the ceiling shut after the first burst. */
    @Test
    void staysOpenUnderSustainedFire() {
        VoiceLimiter limiter = new VoiceLimiter(VoiceLimiter.MAX_VOICES);
        long laser = (long) (SoundFx.LASER.seconds() * SECOND);
        int played = 0;

        // Two players on the eleven-tick cooldown for ten seconds, at sixty ticks a second.
        for (int tick = 0; tick < 600; tick++) {
            long now = tick * SECOND / 60;
            if (tick % 11 == 0 || tick % 11 == 5) {
                if (limiter.claim(now, laser)) {
                    played++;
                }
            }
        }

        assertTrue(played > 100, "sustained fire must keep sounding, got " + played);
    }

    /**
     * The reason a per-effect ceiling exists at all.
     *
     * The boss gun is a 1.6-second burst retriggered five times a second, so on a bare global
     * ceiling it takes every slot and the player's own gun goes silent for the whole boss fight --
     * which is exactly when the player most needs to hear it.
     */
    @Test
    void oneRepeaterCannotStarveEveryOtherEffect() {
        VoiceLimiter global = new VoiceLimiter(VoiceLimiter.MAX_VOICES);
        VoiceLimiter bossGun = new VoiceLimiter(VoiceLimiter.MAX_VOICES_PER_EFFECT);
        VoiceLimiter laser = new VoiceLimiter(VoiceLimiter.MAX_VOICES_PER_EFFECT);
        long gunLength = (long) (SoundFx.BOSS_GUN.seconds() * SECOND);
        long laserLength = (long) (SoundFx.LASER.seconds() * SECOND);

        // The boss empties its magazine into the mix first.
        for (int shot = 0; shot < 20; shot++) {
            if (global.hasFree(0) && bossGun.claim(0, gunLength)) {
                global.claim(0, gunLength);
            }
        }

        assertEquals(VoiceLimiter.MAX_VOICES_PER_EFFECT, bossGun.live(0),
                "the boss gun is held to its own share");
        assertTrue(global.hasFree(0), "and has left room for everyone else");
        assertTrue(laser.claim(0, laserLength) && global.hasFree(0),
                "so the player's gun is still audible mid boss fight");
    }

    /** The case this exists for: a wall of long voices must not exceed the ceiling. */
    @Test
    void boundsVoicesWhenEverythingFiresAtOnce() {
        VoiceLimiter limiter = new VoiceLimiter(VoiceLimiter.MAX_VOICES);
        long longest = (long) (SoundFx.SHIP_EXPLOSION.seconds() * SECOND);

        for (int i = 0; i < 200; i++) {
            limiter.claim(0, longest);
        }

        assertEquals(VoiceLimiter.MAX_VOICES, limiter.live(0),
                "however many are asked for, only the ceiling may be sounding");
    }

    @Test
    void survivesNanoTimeWrappingPastZero() {
        VoiceLimiter limiter = new VoiceLimiter(1);
        long nearWrap = Long.MAX_VALUE - SECOND / 2;

        assertTrue(limiter.claim(nearWrap, SECOND));
        // The expiry has overflowed into negative territory; the slot must still read as busy.
        assertFalse(limiter.claim(nearWrap, SECOND));
        assertEquals(1, limiter.live(nearWrap));
    }
}
