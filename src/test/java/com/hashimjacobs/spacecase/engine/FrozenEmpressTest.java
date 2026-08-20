package com.hashimjacobs.spacecase.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PilotedMech;
import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four-headed fight.
 *
 * {@code HydraTest} proves the same properties, but it builds {@code Boss.HYDRA} and so only ever
 * exercises three heads. Everything the Empress adds is a head count the arc maths had never been
 * flown at: {@code BossTest.anyNumberOfHeadsGetsItsOwnArc} proves the angles differ, which is not
 * the same as proving four necks stay apart and inside the arena over a full sweep.
 */
class FrozenEmpressTest {

    private static World withEmpress() {
        World world = new World(GameMode.SOLO);
        EnemyShip torso = new EnemyShip(Boss.FROZEN_EMPRESS, 400, 90, 1);
        world.addEnemy(torso);
        for (EnemyShip head : torso.parts()) {
            world.addEnemy(head);
        }
        return world;
    }

    @Test
    void theEmpressArrivesWithFourHeads() {
        World world = withEmpress();

        assertEquals(4, world.boss().parts().size());
        assertEquals(5, world.enemies().size(), "a torso and four heads");
    }

    /** Reach plus the holding depth has to stay inside the arena or the culler eats the heads. */
    @Test
    void theNecksNeverSwingOutOfTheArena() {
        World world = withEmpress();
        List<EnemyShip> heads = List.copyOf(world.boss().parts());

        for (int tick = 0; tick < 3000; tick++) {
            world.update();
            world.sweep();
        }

        for (EnemyShip head : heads) {
            assertTrue(head.isAlive(), "a head was culled mid-fight");
            assertTrue(head.y() > -head.height() && head.y() < GameConfig.HEIGHT,
                    "head left the arena at y=" + head.y());
        }
    }

    /**
     * No two of the four heads may ever sit on top of each other.
     *
     * The failure this guards against is specific and was already hit once at three heads: phase
     * offsets alone do not separate necks, because heads a fixed fraction of a cycle apart still
     * share a sine value twice per cycle. A fourth neck adds a fourth chance of that collision.
     */
    @Test
    void theFourHeadsNeverOverlap() {
        World world = withEmpress();
        List<EnemyShip> heads = List.copyOf(world.boss().parts());

        for (int tick = 0; tick < 400; tick++) {
            world.update();
            for (int a = 0; a < heads.size(); a++) {
                for (int b = a + 1; b < heads.size(); b++) {
                    double gap = Math.hypot(heads.get(a).centerX() - heads.get(b).centerX(),
                            heads.get(a).centerY() - heads.get(b).centerY());
                    assertTrue(gap > 30,
                            "heads " + a + " and " + b + " were " + gap + " apart at tick " + tick);
                }
            }
        }
    }

    /**
     * Every part of a flagship counts all of its siblings, whatever the flagship calls them.
     *
     * This is the number EnemyWeapons multiplies the part cooldown by, and the reason it counts
     * parts rather than {@code Boss.heads()}. The rig is the case that separates the two: it
     * declares two heads and fields three targets, an arm, an arm and a cockpit. Deriving from
     * the head count would have given its parts a 1.6 factor where they were tuned at 2.4 --
     * fifty per cent more fire in a shipped fight, from a change meant to leave it alone.
     */
    @Test
    void everyPartCountsItsSiblingsRatherThanTheDeclaredHeads() {
        assertPartsAllReport(new EnemyShip(Boss.HYDRA, 400, 90, 1), 3);
        assertPartsAllReport(new EnemyShip(Boss.FROZEN_EMPRESS, 400, 90, 1), 4);

        // Two declared heads, three actual parts. The rig must keep the three it was tuned at.
        EnemyShip rig = new PilotedMech(Boss.VAUNT, 400, 90, 1);
        assertEquals(2, Boss.VAUNT.heads(), "if the rig's declared head count changed, retune it");
        assertPartsAllReport(rig, 3);
    }

    /**
     * The three-part flagships keep exactly the factor they were tuned at, and four scales.
     *
     * The sibling count above is only half the guarantee -- it proves the parts can be counted,
     * not that the cooldown is derived from that count. This pins the arithmetic itself, so
     * replacing the derivation with a constant fails here rather than silently retuning the hydra
     * and the rig.
     */
    @Test
    void thePartCooldownIsDerivedFromThePartCount() {
        assertEquals(2.4, EnemyWeapons.partCooldownFactor(3), 1e-9,
                "the hydra and the rig were tuned at 2.4 and must stay there");
        assertEquals(3.2, EnemyWeapons.partCooldownFactor(4), 1e-9,
                "a fourth part earns a longer cooldown, not a thicker barrage");
        assertEquals(0.8, EnemyWeapons.partCooldownFactor(1), 1e-9,
                "a lone part is still slower than a whole flagship");
    }

    /**
     * A fourth head does not put a fourth ball of acid in the air either.
     *
     * The bullet cooldown was only half the barrage. Each part also spits one acid ball on its own
     * timer, and what is on screen is parts x fuse / cooldown -- so a flat cooldown meant a fourth
     * part raised the count from about three to nearly five, against a fuse sized for three.
     */
    @Test
    void aFourthHeadDoesNotThickenTheAcid() {
        EnemyShip hydra = new EnemyShip(Boss.HYDRA, 400, 90, 1);
        EnemyShip empress = new EnemyShip(Boss.FROZEN_EMPRESS, 400, 90, 1);

        assertEquals(210, EnemyWeapons.rocketCooldownFor(hydra.parts().get(0)),
                "the hydra's heads were tuned at 210 and must stay there");
        assertEquals(280, EnemyWeapons.rocketCooldownFor(empress.parts().get(0)),
                "a fourth part earns a longer fuse gap, not a fifth ball on screen");

        assertEquals(onScreen(hydra), onScreen(empress), 0.05,
                "acid on screen should not depend on how many parts a flagship fields");
    }

    /** Roughly how many balls a flagship's parts hold in the air at once. */
    private static double onScreen(EnemyShip flagship) {
        int parts = flagship.parts().size();
        return parts * 240.0 / EnemyWeapons.rocketCooldownFor(flagship.parts().get(0));
    }

    private static void assertPartsAllReport(EnemyShip flagship, int expected) {
        assertEquals(expected, flagship.parts().size(),
                flagship.boss() + " should field " + expected + " parts");
        for (EnemyShip part : flagship.parts()) {
            assertEquals(expected, part.siblingParts(),
                    flagship.boss() + " part sees the wrong sibling count");
        }
        assertEquals(1, flagship.siblingParts(), "a flagship is not one of its own parts");
    }
}
