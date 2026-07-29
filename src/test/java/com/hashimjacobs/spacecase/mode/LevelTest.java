package com.hashimjacobs.spacecase.mode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Levels are the only place progression is configured, so their data is worth pinning down. */
class LevelTest {

    @Test
    void everyLevelHasItsOwnSky() {
        Set<Sprite> seen = new HashSet<>();
        for (Level level : Level.values()) {
            assertEquals(3, level.layers().size(), level + " needs a far, mid and near layer");
            for (Sprite layer : level.layers()) {
                assertTrue(seen.add(layer),
                        layer + " is shared between levels, so two levels look alike");
            }
        }
    }

    @Test
    void everyLevelHasItsOwnBoss() {
        Set<Boss> seen = new HashSet<>();
        for (Level level : Level.values()) {
            assertTrue(seen.add(level.boss()),
                    level.boss() + " guards more than one level");
        }
    }

    @Test
    void everyBossIsUsedBySomeLevel() {
        List<Boss> claimed = new ArrayList<>();
        for (Level level : Level.values()) {
            claimed.add(level.boss());
        }
        for (Boss boss : Boss.values()) {
            assertTrue(claimed.contains(boss), boss + " is unreachable: no level fields it");
        }
    }

    @Test
    void everyLevelFieldsWavesBeforeItsBoss() {
        for (Level level : Level.values()) {
            assertTrue(level.wavesBeforeBoss() > 0,
                    level + " would open on its boss with no waves first");
        }
    }

    @Test
    void theLastLevelWrapsBackToTheFirst() {
        Level[] all = Level.values();
        Level last = all[all.length - 1];
        assertEquals(all[0], last.next(), "the run loops rather than ending");
    }

    @Test
    void nextWalksTheLevelsInOrder() {
        Level[] all = Level.values();
        for (int i = 0; i < all.length - 1; i++) {
            assertEquals(all[i + 1], all[i].next(), all[i] + " should lead to the next level");
        }
    }

    @Test
    void everyLevelIsLabelled() {
        for (Level level : Level.values()) {
            assertTrue(level.label() != null && !level.label().isBlank(),
                    level + " needs a label for the HUD");
        }
    }
}
