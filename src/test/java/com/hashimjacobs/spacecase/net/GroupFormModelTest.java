package com.hashimjacobs.spacecase.net;

import java.util.Set;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The create-group form's rule: a pilot asks for help only with somewhere they have reached. */
class GroupFormModelTest {

    private static final Level SECOND = Galaxy.VERDANCE.levels().get(1);
    private static final Level FOURTH = Galaxy.VERDANCE.levels().get(3);

    /** A pilot who has reached Verdance's second and fourth levels, and Ashfall. */
    private static GroupFormModel form() {
        return new GroupFormModel(
                galaxy -> galaxy == Galaxy.ASHFALL,
                Set.of(SECOND, FOURTH)::contains,
                Difficulty.NORMAL);
    }

    @Test
    void aNewFormIsPublicForFourOnTheFirstLevelAtThePilotsOwnDifficulty() {
        GroupSpec spec = form().spec();

        assertTrue(spec.isPublic());
        assertEquals(4, spec.maxPlayers());
        assertEquals(Galaxy.VERDANCE.first(), spec.level());
        assertEquals(Difficulty.NORMAL, spec.difficulty());
    }

    @Test
    void playersStepThroughTwoToFourAndWrap() {
        GroupFormModel form = form();

        form.cycleMax(1);
        assertEquals(2, form.maxPlayers(), "past four comes two");
        form.cycleMax(1);
        assertEquals(3, form.maxPlayers());
        form.cycleMax(-1);
        form.cycleMax(-1);
        assertEquals(4, form.maxPlayers(), "and back from two comes four");
    }

    @Test
    void onlyGalaxiesThePilotHasReachedAreOffered() {
        GroupFormModel form = form();

        form.cycleGalaxy(1);
        assertEquals(Galaxy.ASHFALL, form.galaxy());
        assertEquals(Galaxy.ASHFALL.first(), form.level(), "a new galaxy opens on its first level");
        form.cycleGalaxy(1);
        assertEquals(Galaxy.VERDANCE, form.galaxy(), "the three never reached are skipped");
    }

    @Test
    void levelsThePilotHasNotReachedAreSkipped() {
        GroupFormModel form = form();

        form.cycleLevel(1);
        assertEquals(SECOND, form.level());
        form.cycleLevel(1);
        assertEquals(FOURTH, form.level(), "the third was never unlocked");
        form.cycleLevel(1);
        assertEquals(Galaxy.VERDANCE.first(), form.level());
        form.cycleLevel(-1);
        assertEquals(FOURTH, form.level());
    }

    @Test
    void aPilotWithNothingUnlockedCanStillOpenTheFirstLevel() {
        GroupFormModel form = new GroupFormModel(galaxy -> false, level -> false, Difficulty.EASY);

        form.cycleGalaxy(1);
        form.cycleLevel(1);

        assertEquals(Level.values()[0], form.level());
    }

    @Test
    void visibilityTogglesAndTheSpecSaysSo() {
        GroupFormModel form = form();
        form.cycleVisibility();

        assertFalse(form.spec().isPublic());
        assertTrue(form.spec().encode("ACE").startsWith("PRIVATE,4,VERDANCE,"));
    }

    @Test
    void theSpecEncodesTheSixFieldsTheRelayExpects() {
        GroupSpec spec = new GroupSpec(true, 3, Galaxy.ASHFALL.first(), Difficulty.HARD);

        assertEquals("PUBLIC,3,ASHFALL," + Galaxy.ASHFALL.first().name() + ",HARD,ACE",
                spec.encode("ACE"));
        assertEquals(6, spec.encode("A,CE").split(",").length,
                "a comma in a name must not become a seventh field");
    }

    @Test
    void aGroupSeatsTwoToFour() {
        assertThrows(IllegalArgumentException.class,
                () -> new GroupSpec(true, 1, Level.values()[0], Difficulty.NORMAL));
        assertThrows(IllegalArgumentException.class,
                () -> new GroupSpec(true, 5, Level.values()[0], Difficulty.NORMAL));
    }
}
