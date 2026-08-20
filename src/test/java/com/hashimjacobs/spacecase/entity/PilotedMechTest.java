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
            assertTrue(first.x() >= -first.width() && first.x() <= 996,
                    "the rig walked out of the arena at tick " + tick);
        }
    }
}
