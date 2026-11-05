package com.hashimjacobs.spacecase.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import org.junit.jupiter.api.Test;

/** A flagship's death loop is scenery: outside the checksum, left only by arts that carry one. */
class WreckTest {

    @Test
    void aWreckNeverTouchesTheChecksumAndOnlyArtsWithADeathLoopLeaveOne() {
        World world = new World(GameMode.SOLO);
        world.enterLevel(Level.values()[0]);
        long before = world.checksum();
        for (Boss boss : Boss.values()) {
            EnemyShip flagship = new EnemyShip(boss, 300, 80, 1);
            world.addWreck(flagship);
            assertEquals(before, world.checksum(), boss.name());
        }
        long expected = 0;
        for (Boss boss : Boss.values()) {
            if (boss.art().has(BossArt.State.DEATH)) {
                expected++;
            }
        }
        assertEquals(expected, world.wrecks().size());
        for (World.Wreck wreck : world.wrecks()) {
            assertEquals(0, wreck.frameAt(world.tick()));
            assertTrue(wreck.art().deathTicks() > 0);
        }
        // Sweeping at a tick far past any loop clears the field again.
        for (int i = 0; i < 100_000; i++) {
            world.tickScenery();
        }
        world.sweep();
        assertEquals(0, world.wrecks().size());
    }
}
