package com.hashimjacobs.spacecase.garage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hashimjacobs.spacecase.asset.ChassisArt;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import org.junit.jupiter.api.Test;

/** The CG hull's pose order is the lean order the rest of the garage already uses. */
class ChassisArtPosesTest {

    @Test
    void posesFollowTheLeanOrderTheChassisTableUses() {
        assertEquals(PlayerShip.Lean.values().length, ChassisArt.POSES.length);
        for (int i = 0; i < ChassisArt.POSES.length; i++) {
            // The bought chassis resolve their frames by name in lean order; the CG poses use the
            // same words, so the frame at lean i must carry pose i's name.
            String pose = Chassis.INTERCEPTOR.pose(Livery.MILITIA_GREEN, i, false, false).name();
            assertTrue(pose.endsWith("_" + ChassisArt.POSES[i].toUpperCase().replace('-', '_')), pose);
        }
    }

    @Test
    void everyChassisAndPaintResolvesToItsOwnArtOrToNothing() {
        for (Chassis chassis : Chassis.values()) {
            for (Livery livery : Livery.values()) {
                ChassisArt art = ChassisArt.named(chassis.name() + "_" + livery.name());
                assertTrue(art == null || art.name().equals(chassis.name() + "_" + livery.name()));
            }
        }
        assertNull(Kit.STOCK.art(), "stock has no part");
    }
}
