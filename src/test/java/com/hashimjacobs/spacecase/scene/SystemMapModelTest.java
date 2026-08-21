package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.engine.GamepadMapping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.SaveGames;
import com.hashimjacobs.spacecase.scene.SystemMapModel.NodeState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map's rules, with nothing drawn.
 *
 * The whole reason the cursor and the node states live apart from {@link SystemMapView} is so this
 * file can exist: it touches {@code KeyCode} and Preferences, neither of which starts the JavaFX
 * toolkit, so it runs on a headless CI runner like the rest of the suite.
 */
class SystemMapModelTest {

    private final Preferences scratch =
            Preferences.userRoot().node("space-case-map-test-" + System.nanoTime());

    @AfterEach
    void removeScratchNode() throws BackingStoreException {
        scratch.removeNode();
        scratch.flush();
    }

    private SaveGames saves() {
        return SaveGames.load(scratch);
    }

    private SystemMapModel map(SaveGames saves) {
        return new SystemMapModel(GameMode.SOLO, Galaxy.values()[0], saves, null);
    }

    @Test
    void everyLevelIsOnTheMapWhateverItsState() {
        SystemMapModel map = map(saves());

        assertEquals(Galaxy.LEVELS_PER_GALAXY, map.nodes().size(),
                "a locked level is shown, not hidden -- that is the point of a map");
    }

    @Test
    void aFreshCampaignOpensOnTheFirstLevelWithTheRestLocked() {
        SystemMapModel map = map(saves());

        assertEquals(NodeState.CURRENT, map.nodes().get(0).state());
        for (int i = 1; i < map.nodes().size(); i++) {
            assertEquals(NodeState.LOCKED, map.nodes().get(i).state(),
                    map.nodes().get(i).level() + " should still be locked");
        }
        assertEquals(0, map.cursor(), "the cursor starts where the campaign wants you");
    }

    /** Clearing a level marks it done and opens exactly the next one. */
    @Test
    void statesFollowWhatHasBeenCleared() {
        SaveGames saves = saves();
        saves.recordClear(GameMode.SOLO, Level.values()[0]);
        saves.recordClear(GameMode.SOLO, Level.values()[1]);

        SystemMapModel map = map(saves);

        assertEquals(NodeState.CLEARED, map.nodes().get(0).state());
        assertEquals(NodeState.CLEARED, map.nodes().get(1).state());
        assertEquals(NodeState.CURRENT, map.nodes().get(2).state(), "the next one is up");
        assertEquals(NodeState.LOCKED, map.nodes().get(3).state());
    }

    /**
     * The cursor opens on the level to fly next, not on the first.
     *
     * So arriving deep into a campaign and pressing fire does the obvious thing rather than
     * replaying level one.
     */
    @Test
    void theCursorOpensOnTheNextLevelToFly() {
        SaveGames saves = saves();
        for (int i = 0; i < 4; i++) {
            saves.recordClear(GameMode.SOLO, Level.values()[i]);
        }

        assertEquals(4, map(saves).cursor());
    }

    /**
     * Along the route the cursor stops at both ends rather than wrapping.
     *
     * Deliberately unlike {@link MenuNavigator}, which wraps: a list of rows is a loop with no ends,
     * but a route has a first level and a last one, and jumping from the tenth to the first reads as
     * a glitch.
     */
    @Test
    void theCursorStopsAtBothEndsOfTheRoute() {
        SystemMapModel map = map(saves());

        for (int i = 0; i < 20; i++) {
            map.handleKey(KeyCode.LEFT);
        }
        assertEquals(0, map.cursor(), "walking off the start should stay at the start");

        for (int i = 0; i < 40; i++) {
            map.handleKey(KeyCode.RIGHT);
        }
        assertEquals(map.nodes().size() - 1, map.cursor(), "and off the end, stay at the end");
    }

    /** Up and down swap rows at the same column, and are the same move with only two rows. */
    @Test
    void upAndDownSwapRowsAtTheSameColumn() {
        SystemMapModel map = map(saves());
        int columnOf3 = 3;
        map.handleKey(KeyCode.RIGHT);
        map.handleKey(KeyCode.RIGHT);
        map.handleKey(KeyCode.RIGHT);
        assertEquals(columnOf3, map.cursor());

        map.handleKey(KeyCode.DOWN);
        int mirrored = map.cursor();
        assertEquals(map.nodes().size() - 1 - columnOf3, mirrored);
        assertEquals(map.nodes().get(columnOf3).x(), map.nodes().get(mirrored).x(), 1e-9,
                "the two rows should line up in columns, or up and down jump sideways");

        map.handleKey(KeyCode.UP);
        assertEquals(columnOf3, map.cursor(), "and back again");
    }

    @Test
    void wasdMirrorsTheArrowKeys() {
        SystemMapModel byArrows = map(saves());
        SystemMapModel byLetters = map(saves());

        byArrows.handleKey(KeyCode.RIGHT);
        byArrows.handleKey(KeyCode.RIGHT);
        byLetters.handleKey(KeyCode.D);
        byLetters.handleKey(KeyCode.D);

        assertEquals(byArrows.cursor(), byLetters.cursor());
    }

    /** Same contract MenuNavigator keeps: unclaimed keys pass through to the scene filter. */
    @Test
    void unrelatedKeysAreNotConsumed() {
        SystemMapModel map = map(saves());

        assertFalse(map.handleKey(KeyCode.F11), "F11 must reach the fullscreen handler");
        assertFalse(map.handleKey(KeyCode.TAB));
        assertTrue(map.handleKey(KeyCode.RIGHT), "and the ones it owns are consumed");
    }

    @Test
    void anAvailableLevelLaunches() {
        List<Level> launched = new ArrayList<>();
        SystemMapModel map = map(saves());
        map.setOnLaunch(launched::add);

        map.handleKey(KeyCode.ENTER);

        assertEquals(1, launched.size());
        assertSame(Galaxy.values()[0].first(), launched.get(0));
    }

    /**
     * A locked level says why instead of doing nothing.
     *
     * Silence is indistinguishable from a dropped keypress, which sends the player hunting for a
     * broken control rather than for the level they have not finished.
     */
    @Test
    void aLockedLevelRefusesAndSaysWhat() {
        List<Level> launched = new ArrayList<>();
        SystemMapModel map = map(saves());
        map.setOnLaunch(launched::add);

        map.handleKey(KeyCode.RIGHT);
        map.handleKey(KeyCode.ENTER);

        assertTrue(launched.isEmpty(), "a locked level must not fly");
        assertTrue(map.message().contains(Level.values()[0].label()),
                "it should name the level in the way, got: " + map.message());
    }

    @Test
    void movingClearsTheRefusalMessage() {
        SystemMapModel map = map(saves());
        map.handleKey(KeyCode.RIGHT);
        map.handleKey(KeyCode.ENTER);
        assertFalse(map.message().isEmpty());

        map.handleKey(KeyCode.LEFT);

        assertTrue(map.message().isEmpty(), "a stale reason is worse than none");
    }

    @Test
    void escapeGoesBack() {
        int[] backs = {0};
        SystemMapModel map = map(saves());
        map.setOnBack(() -> backs[0]++);

        map.handleKey(KeyCode.ESCAPE);

        assertEquals(1, backs[0]);
    }

    /**
     * And so does the pad's cancel button, which is B.
     *
     * Start already backed out of here, because the pause button speaks Escape -- but B is where a
     * player's thumb goes, and it was bound to nothing at all.
     */
    @Test
    void thePadsCancelButtonGoesBackTo() {
        int[] backs = {0};
        SystemMapModel map = map(saves());
        map.setOnBack(() -> backs[0]++);

        assertTrue(map.handleKey(GamepadMapping.MENU_CANCEL), "cancel has to be consumed here");
        assertEquals(1, backs[0]);
    }

    @Test
    void everyNodeSitsInsideTheMap() {
        for (SystemMapModel.Node node : map(saves()).nodes()) {
            assertTrue(node.x() >= 0 && node.x() <= 1, node.level() + " is off the map: " + node.x());
            assertTrue(node.y() >= 0 && node.y() <= 1, node.level() + " is off the map: " + node.y());
        }
    }
}
