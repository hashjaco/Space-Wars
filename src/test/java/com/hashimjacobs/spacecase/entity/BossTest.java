package com.hashimjacobs.spacecase.entity;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTest {

    @Test
    void phaseSwitchesOnThirdsOfTheHealthBar() {
        Boss boss = Boss.SENTINEL;
        assertEquals(boss.phaseFor(1.0), boss.phaseFor(0.67), "the top third is one phase");
        assertEquals(boss.phaseFor(0.66), boss.phaseFor(0.34), "the middle third is one phase");
        assertEquals(boss.phaseFor(0.33), boss.phaseFor(0.0), "the bottom third is one phase");
        assertTrue(boss.phaseFor(1.0) != boss.phaseFor(0.5), "thirds must differ from each other");
    }

    @Test
    void aDeadBossStillReportsAPhase() {
        for (Boss boss : Boss.values()) {
            assertTrue(boss.phaseFor(0.0) != null, boss + " must answer for zero health");
            assertTrue(boss.phaseFor(-0.1) != null, boss + " must answer for overkill damage");
        }
    }

    @Test
    void healthRisesWithLevelOrder() {
        Boss[] all = Boss.values();
        for (int i = 0; i < all.length - 1; i++) {
            assertTrue(all[i + 1].health() > all[i].health(),
                    all[i + 1] + " should be tougher than " + all[i]);
        }
    }

    @Test
    void scoreRisesWithLevelOrder() {
        Boss[] all = Boss.values();
        for (int i = 0; i < all.length - 1; i++) {
            assertTrue(all[i + 1].scoreValue() > all[i].scoreValue(),
                    all[i + 1] + " should be worth more than " + all[i]);
        }
    }

    @Test
    void everyBossHasItsOwnArt() {
        Set<BossArt> seen = new HashSet<>();
        for (Boss boss : Boss.values()) {
            assertTrue(seen.add(boss.art()), boss.art() + " is shared by two bosses");
        }
    }

    @Test
    void everyBossIsLabelled() {
        for (Boss boss : Boss.values()) {
            assertTrue(boss.label() != null && !boss.label().isBlank(),
                    boss + " needs a label for the boss bar");
        }
    }

    @Test
    void everyBossAnimationLoopsOverSeveralFrames() {
        for (Boss boss : Boss.values()) {
            BossArt art = boss.art();
            assertTrue(art.frameCount() > 1, art + " is not animated");
            assertTrue(art.ticksPerFrame() > 0, art + " would divide by zero when picking a frame");
            assertEquals(0, art.frameIndexAt(0), art + " should start on its first frame");
            int lastTick = art.frameCount() * art.ticksPerFrame();
            assertEquals(0, art.frameIndexAt(lastTick), art + " should loop back round");
        }
    }

    @Test
    void aBossIsBiggerThanAnOrdinaryEnemy() {
        for (Boss boss : Boss.values()) {
            assertTrue(boss.art().width() > Level.values()[0].enemySprite(EnemyShip.EnemyKind.CRUISER).width(),
                    boss + " should out-size the largest ordinary enemy");
        }
    }
}
