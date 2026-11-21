package com.hashimjacobs.spacecase.net;

import java.util.List;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game board's rules with no screen and no relay: what the filters keep, what a row says, and
 * when it is time to ask again.
 */
class GroupBoardModelTest {

    private static final Level VERDANCE_3 = Galaxy.VERDANCE.levels().get(2);
    private static final Level ASHFALL_1 = Galaxy.ASHFALL.first();

    private static GroupListing listing(String host, int players, int max, Level level,
                                        Difficulty difficulty, boolean playing) {
        return new GroupListing("ABC234", host, players, max, level, difficulty, playing);
    }

    private static GroupBoardModel boardOf(GroupListing... listings) {
        GroupBoardModel model = new GroupBoardModel();
        model.loaded(List.of(listings));
        return model;
    }

    @Test
    void aFreshBoardIsLoadingAndAsksAtOnce() {
        GroupBoardModel model = new GroupBoardModel();

        assertEquals(GroupBoardModel.State.LOADING, model.state());
        assertTrue(model.refreshDue(0));
        assertEquals("Looking for groups...", model.status());
    }

    @Test
    void theBoardAsksAgainEveryFewSecondsAndNeverTwiceAtOnce() {
        GroupBoardModel model = new GroupBoardModel();
        model.asked(1_000);
        assertFalse(model.refreshDue(9_000_000_000L), "one request is still out");

        model.loaded(List.of());
        assertFalse(model.refreshDue(2_000_000_000L), "too soon after the last");
        assertTrue(model.refreshDue(5_000_001_000L));
    }

    @Test
    void changingAFilterAsksAgainWithoutWaiting() {
        GroupBoardModel model = new GroupBoardModel();
        model.asked(1_000);
        model.loaded(List.of());

        model.refreshNow();

        assertTrue(model.refreshDue(1_001));
    }

    @Test
    void theGalaxyFilterStepsThroughAnyThenEachGalaxyAndBack() {
        GroupBoardModel model = boardOf(
                listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false),
                listing("NOVA", 1, 2, ASHFALL_1, Difficulty.HARD, false));
        assertEquals("ANY", model.galaxyLabel());
        assertEquals("", model.galaxyQuery());
        assertEquals(2, model.visible().size());

        model.cycleGalaxy(1);
        assertEquals("VERDANCE", model.galaxyLabel());
        assertEquals("VERDANCE", model.galaxyQuery());
        assertEquals(List.of("ACE"), model.visible().stream().map(GroupListing::host).toList());

        model.cycleGalaxy(-1);
        assertEquals("ANY", model.galaxyLabel());
        model.cycleGalaxy(-1);
        assertEquals(Galaxy.values()[Galaxy.values().length - 1].name(), model.galaxyQuery(),
                "stepping back from any wraps to the last galaxy");
    }

    @Test
    void theDifficultyFilterKeepsOnlyThatPreset() {
        GroupBoardModel model = boardOf(
                listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false),
                listing("NOVA", 1, 2, ASHFALL_1, Difficulty.HARD, false));
        while (!"HARD".equals(model.difficultyQuery())) {
            model.cycleDifficulty(1);
        }

        assertEquals(List.of("NOVA"), model.visible().stream().map(GroupListing::host).toList());
    }

    @Test
    void openSeatsOnlyHidesFullGroupsWhichAreOtherwiseShownButCannotBeJoined() {
        GroupBoardModel model = boardOf(
                listing("FULL", 2, 2, VERDANCE_3, Difficulty.NORMAL, true),
                listing("ROOMY", 1, 4, VERDANCE_3, Difficulty.NORMAL, false));
        assertEquals(2, model.visible().size());
        assertFalse(model.canJoin(0), "a full group is on the board but not for pressing");
        assertTrue(model.canJoin(1));

        model.toggleOpenOnly();

        assertEquals(List.of("ROOMY"), model.visible().stream().map(GroupListing::host).toList());
        assertEquals("OPEN SEATS ONLY", model.openOnlyLabel());
    }

    @Test
    void aGroupInFlightWithASeatFreeCanBeJoined() {
        GroupBoardModel model = boardOf(listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, true));

        assertTrue(model.canJoin(0), "help arrives at the start of the next level");
        assertTrue(model.at(0).orElseThrow().detail().contains("next level"));
    }

    @Test
    void aRowPastTheEndOfTheListIsEmpty() {
        GroupBoardModel model = boardOf(listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false));

        assertTrue(model.at(1).isEmpty());
        assertTrue(model.at(-1).isEmpty());
        assertFalse(model.canJoin(1));
    }

    @Test
    void theStatusLineSaysWhatThePilotIsLookingAt() {
        GroupBoardModel empty = boardOf();
        assertTrue(empty.status().startsWith("Nobody is asking for help"));

        GroupBoardModel filteredOut = boardOf(listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false));
        filteredOut.cycleGalaxy(-1);
        assertEquals("No groups match. Loosen a filter.", filteredOut.status());

        assertEquals("1 group open",
                boardOf(listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false)).status());

        GroupListing[] many = new GroupListing[GroupBoardModel.ROWS + 2];
        java.util.Arrays.fill(many, listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false));
        assertTrue(boardOf(many).status().startsWith("Showing " + GroupBoardModel.ROWS + " of"));
    }

    @Test
    void anUnreachableRelayKeepsTheLastListOnScreen() {
        GroupBoardModel model = boardOf(listing("ACE", 1, 3, VERDANCE_3, Difficulty.NORMAL, false));

        model.failed();

        assertEquals(GroupBoardModel.State.FAILED, model.state());
        assertEquals(1, model.visible().size());
        assertEquals("1 group open", model.status());

        GroupBoardModel never = new GroupBoardModel();
        never.failed();
        assertTrue(never.status().startsWith("The relay did not answer"));
    }

    @Test
    void aRowReadsWhereHowHardAndHowFull() {
        GroupListing listing = listing("ACE", 2, 3, VERDANCE_3, Difficulty.HARD, false);

        assertEquals("VERDANCE 3  ·  HARD  ·  2/3", listing.value());
    }
}
