package com.hashimjacobs.spacecase.entity;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.asset.BossArt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vaunt's rig: break the guard, then shoot the man.
 *
 * The sequence is the fight, so it is the thing worth pinning. Everything else about this boss is
 * numbers in an enum row, but the cockpit's rule is real logic, and it fails quietly -- get it
 * wrong and a galaxy finale ends in one lucky opening volley with nothing in the logs to say why.
 */
class PilotedMechTest {

    private static PilotedMech rig() {
        return new PilotedMech(Boss.VAUNT, 400, 90, 1);
    }

    private static List<EnemyShip> arms(PilotedMech rig) {
        return rig.parts().stream().filter(part -> part.bossArt() == BossArt.FORGE_RIG_ARM).toList();
    }

    private static EnemyShip cockpit(PilotedMech rig) {
        return rig.parts().stream()
                .filter(part -> part.bossArt() == BossArt.FORGE_RIG_COCKPIT)
                .findFirst().orElseThrow();
    }

    @Test
    void theRigCarriesTwoArmsAndACockpit() {
        PilotedMech rig = rig();

        assertEquals(3, rig.parts().size());
        assertEquals(2, arms(rig).size(), "two arms, one each side");
        assertSame(BossArt.FORGE_RIG_COCKPIT, cockpit(rig).bossArt());
    }

    /**
     * The arms do not render as the pilot's canopy.
     *
     * The inherited rule hands every part of a flagship the same art, which is right for three
     * identical hydra heads and wrong here. Left alone it put a man behind glass on each shoulder.
     */
    @Test
    void eachPieceDrawsItsOwnArt() {
        PilotedMech rig = rig();

        for (EnemyShip arm : arms(rig)) {
            assertSame(BossArt.FORGE_RIG_ARM, arm.bossArt());
        }
        assertSame(BossArt.FORGE_RIG, rig.bossArt(), "the body keeps the flagship's own frames");
    }

    /** The body is sealed while anything is bolted to it, which every flagship already does. */
    @Test
    void theBodyIsSealedWhileAnyPartLives() {
        PilotedMech rig = rig();
        double before = rig.remainingHealthFraction();

        rig.takeDamage(500);

        assertEquals(before, rig.remainingHealthFraction(), 1e-9,
                "the rig cannot be hurt through its own parts");
    }

    /** The rule this boss exists for. */
    @Test
    void theCockpitIsSealedWhileEitherArmLives() {
        PilotedMech rig = rig();
        EnemyShip cockpit = cockpit(rig);
        List<EnemyShip> arms = arms(rig);
        double sealed = cockpit.remainingHealthFraction();

        cockpit.takeDamage(9999);
        assertEquals(sealed, cockpit.remainingHealthFraction(), 1e-9,
                "the pilot cannot be shot through the guard");

        arms.get(0).takeDamage(9999);
        assertFalse(arms.get(0).isAlive());
        cockpit.takeDamage(9999);
        assertEquals(sealed, cockpit.remainingHealthFraction(), 1e-9,
                "one arm down is not enough");

        arms.get(1).takeDamage(9999);
        assertFalse(arms.get(1).isAlive());

        cockpit.takeDamage(9999);
        assertFalse(cockpit.isAlive(), "with both arms gone the pilot is reachable");
    }

    /** Killing the pilot is what ends the fight, so the body has to follow him down. */
    @Test
    void breakingTheGuardThenTheCockpitOpensTheBody() {
        PilotedMech rig = rig();
        for (EnemyShip arm : arms(rig)) {
            arm.takeDamage(9999);
        }
        cockpit(rig).takeDamage(9999);

        double before = rig.remainingHealthFraction();
        rig.takeDamage(400);

        assertTrue(rig.remainingHealthFraction() < before,
                "with every piece gone the rig itself takes damage");
    }

    /**
     * Nothing outlives the rig.
     *
     * Parts are ordinary entries in the world's enemy list, so a part left alive after its body died
     * would keep firing from wherever it last stood, with no health bar and nothing to shoot.
     */
    @Test
    void noPieceOutlivesTheRig() {
        PilotedMech rig = rig();
        rig.kill();

        for (EnemyShip part : rig.parts()) {
            part.update();
            assertFalse(part.isAlive(), part.bossArt() + " outlived the rig");
        }
    }

    /**
     * The stride is a pure function of age.
     *
     * Same discipline the worm keeps, and for the same two reasons: nothing to restore when a run is
     * resumed, and no draw from the spawn director's generator -- which would shift every later spawn
     * and break the fixed-seed run its tests are pinned against.
     */
    @Test
    void theStrideIsRepeatableAndStaysInTheArena() {
        PilotedMech first = rig();
        PilotedMech second = rig();
        for (int tick = 0; tick < 900; tick++) {
            first.update();
            second.update();
            assertEquals(first.x(), second.x(), 1e-9, "the walk drifted between two identical rigs");
            assertTrue(first.x() >= 0 && first.x() + first.width() <= 996,
                    "the rig walked out of the arena at tick " + tick + ", x=" + first.x()
                            + " width=" + first.width());
        }
    }

    /**
     * The width ceiling, which nothing guarded until Tempest wanted a bigger rig.
     *
     * This assertion used to read {@code first.x() <= 996}, which is vacuously true of anything
     * drawn on screen at all -- it can only catch a rig that has left the arena entirely, never one
     * whose shoulder is hanging over the edge. It now checks the trailing edge, which is the thing
     * the stride can actually push out: {@link PilotedMech} walks the centre of the lane to 0.8 of
     * the arena's breadth and then subtracts half its own width, so a body wider than 398 overhangs
     * no matter how it is placed.
     *
     * Stated as a property of every rig rather than of the two that exist, so a third inherits it.
     */
    @Test
    void noRigIsWiderThanTheStrideLeavesRoomFor() {
        for (Boss flagship : Boss.values()) {
            if (flagship.armArt() == null) {
                continue;
            }
            double widest = 996 * 0.2 * 2;
            assertTrue(flagship.art().width() <= widest,
                    flagship + " is " + flagship.art().width() + " wide, and the stride leaves room"
                            + " for " + widest + " -- it will walk its shoulder off the arena");
        }
    }

    /**
     * A rig must declare heads even though it throws away the parts they imply.
     *
     * {@link PilotedMech} clears the inherited parts and fits its own three, so the head count looks
     * decorative and is not. {@code EnemyShip.bodyShare} hands a flagship all of its authored health
     * when the count is zero, and {@code EnemyWeapons} lets it fire rocket salvos -- so a rig row
     * written with zero would carry 145% of its stated health across body and parts, move every
     * phase boundary with it, and arm the body with something no rig has ever fired.
     */
    @Test
    void everyRigDeclaresItsHeadsSoTheHealthSharesStayRight() {
        for (Boss flagship : Boss.values()) {
            if (flagship.armArt() == null) {
                continue;
            }
            assertTrue(flagship.heads() > 0,
                    flagship + " must declare its heads even though PilotedMech discards them");
            assertEquals(3, new PilotedMech(flagship, 400, 90, 1).parts().size(),
                    flagship + " should field two arms and a cockpit");
        }
    }

    /** Each rig wears its own pods. This was a compile-time constant, so the second one could not. */
    @Test
    void eachRigWearsItsOwnArmPods() {
        assertSame(BossArt.FORGE_RIG_ARM, Boss.VAUNT.armArt());
        assertSame(BossArt.STORM_RIG_ARM, Boss.VAUNT_IN_THE_STORM_RIG.armArt());

        PilotedMech storm = new PilotedMech(Boss.VAUNT_IN_THE_STORM_RIG, 400, 90, 1);
        assertTrue(storm.parts().stream()
                        .anyMatch(part -> part.bossArt() == BossArt.STORM_RIG_ARM),
                "the Storm-Rig is still wearing Ashfall's pods");
        assertFalse(storm.parts().stream()
                        .anyMatch(part -> part.bossArt() == BossArt.FORGE_RIG_ARM),
                "the Storm-Rig is still wearing Ashfall's pods");
    }
}
