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
    void everyChassisResolvesToItsOwnArtOrToNothingWhateverThePaint() {
        for (Chassis chassis : Chassis.values()) {
            ChassisArt art = ChassisArt.named(chassis.name());
            assertTrue(art == null || art.name().equals(chassis.name()));
        }
        assertNull(Kit.STOCK.slug(), "the bare hull has no kit suffix");
        for (Kit kit : Kit.values()) {
            // CgArtProvenanceTest spells the kit directories out, because asset cannot see garage.
            assertTrue(kit == Kit.STOCK || java.util.List.of("fins", "armour", "lance").contains(kit.slug()), kit.name());
        }
    }

    @Test
    void everyChassisAnchorsItsFlameAtItsTail() {
        for (ChassisArt art : ChassisArt.values()) {
            assertTrue(art.tailX() > 0 && art.tailX() < 1, art + " flame off the side of the hull");
            assertTrue(art.tailY() > 0.8 && art.tailY() <= 1, art + " flame not at the tail");
            assertTrue(art.sideTailY() > 0 && art.sideTailY() < 1, art + " flame off the profile");
        }
    }
}
