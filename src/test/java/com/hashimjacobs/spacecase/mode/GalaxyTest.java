package com.hashimjacobs.spacecase.mode;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A galaxy owns ten consecutive levels and nothing maps the two together by hand, so what is worth
 * pinning is the arithmetic that keeps them in step.
 *
 * The first test is the one that matters. Every other file that groups levels -- the cleared-levels
 * mask, the map, the debrief's galaxy premium -- assumes the level count divides evenly into
 * galaxies of ten. Adding a galaxy without its ten levels, or a level without its galaxy, would
 * otherwise fail somewhere far from the cause, as an index off the end of an array.
 */
class GalaxyTest {

    @Test
    void everyGalaxyHasItsFullBlockOfLevels() {
        assertEquals(Galaxy.values().length * Galaxy.LEVELS_PER_GALAXY, Level.values().length,
                "galaxies and levels have drifted apart: every galaxy owns exactly "
                        + Galaxy.LEVELS_PER_GALAXY + " levels, so add them together");
    }

    @Test
    void everyLevelKnowsWhichGalaxyItIsIn() {
        for (Galaxy galaxy : Galaxy.values()) {
            for (Level level : galaxy.levels()) {
                assertSame(galaxy, level.galaxy(), level + " disagrees about its galaxy");
            }
        }
    }

    @Test
    void aGalaxysLevelsAreTenConsecutiveOnesInRunOrder() {
        for (Galaxy galaxy : Galaxy.values()) {
            List<Level> levels = galaxy.levels();
            assertEquals(Galaxy.LEVELS_PER_GALAXY, levels.size(), galaxy + " is the wrong size");
            for (int i = 1; i < levels.size(); i++) {
                assertEquals(levels.get(i - 1).ordinal() + 1, levels.get(i).ordinal(),
                        galaxy + " has a gap in it");
            }
            assertSame(levels.get(0), galaxy.first());
            assertSame(levels.get(levels.size() - 1), galaxy.last());
        }
    }

    @Test
    void positionInAGalaxyRunsOneToTen() {
        for (Galaxy galaxy : Galaxy.values()) {
            List<Level> levels = galaxy.levels();
            for (int i = 0; i < levels.size(); i++) {
                assertEquals(i + 1, levels.get(i).indexInGalaxy(),
                        levels.get(i) + " is not where it thinks it is");
            }
        }
    }

    /**
     * The one asymmetry with {@link Level#next()}, and it is deliberate: the ten legs of a galaxy
     * repeat, but the campaign finishes. A wrapping galaxy would send the last one back to the
     * first and there would be no such thing as completing the game.
     */
    @Test
    void theCampaignEndsRatherThanWrapping() {
        Galaxy[] all = Galaxy.values();
        for (int i = 0; i < all.length - 1; i++) {
            assertSame(all[i + 1], all[i].next(), all[i] + " should lead to the next galaxy");
        }
        assertNull(all[all.length - 1].next(), "the last galaxy is the end of the campaign");
    }

    @Test
    void everyGalaxyIsLabelledAndCarriesAnAccent() {
        for (Galaxy galaxy : Galaxy.values()) {
            assertTrue(galaxy.label() != null && !galaxy.label().isBlank(),
                    galaxy + " needs a label");
            assertNotNull(galaxy.accent(), galaxy + " needs an accent colour");
            assertTrue(galaxy.accent().matches("#[0-9a-fA-F]{6}"),
                    galaxy + " accent must be a six-digit hex string, not " + galaxy.accent());
        }
    }

    @Test
    void galaxiesAreNumberedFromOne() {
        Galaxy[] all = Galaxy.values();
        for (int i = 0; i < all.length; i++) {
            assertEquals(i + 1, all[i].number());
        }
    }

    /**
     * Fifty levels is fifty bits, and the cleared-levels mask is a long. Anything beyond sixty-four
     * silently stops being recordable, so this fails while there is still time to notice.
     */
    @Test
    void theCampaignStillFitsInAClearedLevelsMask() {
        assertTrue(Level.values().length <= Long.SIZE,
                "a long holds " + Long.SIZE + " bits; the cleared-levels mask needs one per level");
    }
}
