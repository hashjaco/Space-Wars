package com.hashimjacobs.spacecase.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundVolumeTest {

    /** A typical MASTER_GAIN range on a Java Sound clip. */
    private static final float MIN = -80f;
    private static final float MAX = 6f;

    @Test
    void fullVolumeIsUnityGain() {
        assertEquals(0f, SoundBank.decibels(1.0, MIN, MAX), 1e-4);
    }

    @Test
    void halfVolumeIsAboutSixDecibelsDown() {
        assertEquals(-6.02f, SoundBank.decibels(0.5, MIN, MAX), 0.01);
    }

    @Test
    void zeroOrNegativeVolumeIsTheFloorNotMinusInfinity() {
        assertEquals(MIN, SoundBank.decibels(0, MIN, MAX));
        assertEquals(MIN, SoundBank.decibels(-0.3, MIN, MAX));
    }

    @Test
    void outOfRangeVolumesClampToTheControl() {
        assertEquals(MIN, SoundBank.decibels(1e-9, MIN, MAX), "a whisper below the floor is the floor");
        assertEquals(MAX, SoundBank.decibels(10, MIN, MAX), "a boost past the ceiling is the ceiling");
    }

    @Test
    void louderVolumeIsNeverQuieter() {
        float previous = SoundBank.decibels(0, MIN, MAX);
        for (int step = 1; step <= 100; step++) {
            float now = SoundBank.decibels(step / 100.0, MIN, MAX);
            assertTrue(now >= previous, "gain went down between " + (step - 1) + "% and " + step + "%");
            previous = now;
        }
    }

    @Test
    void everyEffectHasAnAudibleLevelAtOrBelowUnity() {
        for (SoundFx effect : SoundFx.values()) {
            double level = effect.baseVolume();
            assertTrue(level > 0 && level <= 1, effect + " base volume " + level + " is outside (0, 1]");
        }
    }

    @Test
    void lowHealthWarningSitsUnderTheLaser() {
        // SoundFx: at 0.4 it was the loudest thing in the game while firing on repeat.
        assertTrue(SoundFx.LOW_HEALTH.baseVolume() < SoundFx.LASER.baseVolume());
    }
}
