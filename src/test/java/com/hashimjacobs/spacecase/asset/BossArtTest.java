package com.hashimjacobs.spacecase.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The damage-state model on {@link BossArt}: pure functions of the art and the tick. */
class BossArtTest {

    @Test
    void anArtWithoutDamageLoopsAlwaysIdles() {
        for (BossArt art : BossArt.values()) {
            if (art.has(BossArt.State.HURT) || art.has(BossArt.State.RAGE)) {
                continue;
            }
            assertEquals(BossArt.State.IDLE, art.stateFor(1.0), art.name());
            assertEquals(BossArt.State.IDLE, art.stateFor(0.5), art.name());
            assertEquals(BossArt.State.IDLE, art.stateFor(0.1), art.name());
            assertFalse(art.has(BossArt.State.DEATH), art.name());
            assertEquals(art.frameCount(), art.frameCount(BossArt.State.IDLE), art.name());
        }
    }

    @Test
    void idlePathsAreTheOldPathsAndStatePathsSitInSubdirectories() {
        BossArt art = BossArt.values()[0];
        assertEquals(art.framePath(3), art.framePath(BossArt.State.IDLE, 3));
        assertTrue(art.framePath(BossArt.State.HURT, 1).endsWith("/hurt/1.png"));
        assertTrue(art.framePath(BossArt.State.RAGE, 2).endsWith("/rage/2.png"));
        assertTrue(art.framePath(BossArt.State.DEATH, 12).endsWith("/death/12.png"));
    }

    @Test
    void anArtWithLoopsSwitchesAtTheBossPhaseThresholds() {
        for (BossArt art : BossArt.values()) {
            if (!art.has(BossArt.State.HURT) && !art.has(BossArt.State.RAGE)) {
                continue;
            }
            assertEquals(BossArt.State.IDLE, art.stateFor(1.0), art.name());
            assertEquals(BossArt.State.IDLE, art.stateFor(0.67), art.name());
            BossArt.State underTwoThirds = art.stateFor(0.66);
            assertEquals(art.has(BossArt.State.HURT) ? BossArt.State.HURT : BossArt.State.IDLE, underTwoThirds, art.name());
            BossArt.State underAThird = art.stateFor(0.33);
            assertEquals(art.has(BossArt.State.RAGE) ? BossArt.State.RAGE : underTwoThirds, underAThird, art.name());
            if (art.has(BossArt.State.DEATH)) {
                assertEquals(0, art.deathFrameAt(0), art.name());
                assertEquals(0, art.deathFrameAt(-50), art.name());
                assertEquals(art.frameCount(BossArt.State.DEATH) - 1, art.deathFrameAt(1_000_000), art.name());
                assertEquals(art.frameCount(BossArt.State.DEATH) * art.ticksPerFrame(), art.deathTicks(), art.name());
            }
        }
    }

    @Test
    void loopsAreAPureFunctionOfTheTick() {
        BossArt art = BossArt.values()[0];
        int period = art.frameCount() * art.ticksPerFrame();
        for (int tick = 0; tick < period * 3; tick++) {
            assertEquals(art.frameIndexAt(tick), art.frameIndexAt(tick + period));
            assertEquals(art.frameIndexAt(tick), art.frameIndexAt(BossArt.State.IDLE, tick));
            assertTrue(art.frameIndexAt(tick) < art.frameCount());
        }
    }
}
