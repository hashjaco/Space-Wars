package com.hashimjacobs.spacecase.prefs;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A campaign leaving one machine and arriving on another.
 *
 * Two throwaway nodes stand in for the two machines, which is the whole of what a cloud round trip
 * is once the socket is taken out of it -- the same reason {@code LockstepTest} wires two peers to
 * each other's byte queues instead of opening a relay.
 */
class ProfileTest {

    private final Preferences here =
            Preferences.userRoot().node("space-case-test-here-" + System.nanoTime());
    private final Preferences there =
            Preferences.userRoot().node("space-case-test-there-" + System.nanoTime());

    @AfterEach
    void removeScratchNodes() throws BackingStoreException {
        here.removeNode();
        there.removeNode();
        here.flush();
        there.flush();
    }

    private static SaveSlot aRunAt(Level level) {
        return new SaveSlot(GameMode.SOLO, level, 4, 1,
                List.of(new PlayerShip.Progress(80, 2, 12_400, 30, 5, 200, 90, 60)));
    }

    @Test
    void aProfileCarriesRunsPilotsAndScoresToAnotherMachine() {
        SaveGames.load(here.node("saves")).saveCheckpoint(aRunAt(Level.values()[6]));
        Pilots.load(here.node("pilots")).setName(1, "NOVA");
        Pilots.load(here.node("pilots")).addCareerScore("NOVA", 9000);
        Pilots.load(here.node("pilots")).addCredits("NOVA", 250);
        HighScores.load(here.node("highscores")).submit(GameMode.SOLO, 12_400);

        assertTrue(Profile.restore(there, Profile.export(here)));

        SaveGames arrived = SaveGames.load(there.node("saves"));
        assertTrue(arrived.checkpoint().isPresent());
        assertEquals(Level.values()[6], arrived.checkpoint().get().level());
        Pilots pilots = Pilots.load(there.node("pilots"));
        assertEquals("NOVA", pilots.name(1));
        // A career lives in a child node of pilots, so this is also the assertion that the format
        // carries nesting rather than only a node's own keys.
        assertEquals(9000, pilots.careerScore("NOVA"));
        assertEquals(250, pilots.credits("NOVA"));
        assertEquals(12_400, HighScores.load(there.node("highscores")).best(GameMode.SOLO));
    }

    /**
     * A checkpoint is {@code SaveSlot.encode} output, which is comma-separated. If the profile
     * format ever splits on a comma, this is the assertion that says so: the run arrives as
     * fragments and the level comes back wrong or not at all.
     */
    @Test
    void aCommaSeparatedValueSurvivesTheCommaSeparatedFormat() {
        SaveSlot run = aRunAt(Level.values()[3]);
        SaveGames.load(here.node("saves")).saveCheckpoint(run);

        Profile.restore(there, Profile.export(here));

        assertEquals(run.encode(),
                SaveGames.load(there.node("saves")).checkpoint().orElseThrow().encode());
    }

    /**
     * A download replaces rather than merges. A second machine's own half-finished run must not
     * survive underneath the profile that was pulled over it, or the player ends up with a Continue
     * that belongs to neither machine.
     */
    @Test
    void whatWasHereBeforeIsGoneAfterwards() {
        Pilots.load(there.node("pilots")).setName(1, "LOCAL");
        Pilots.load(there.node("pilots")).addCareerScore("LOCAL", 500);
        Pilots.load(here.node("pilots")).setName(1, "NOVA");

        Profile.restore(there, Profile.export(here));

        Pilots pilots = Pilots.load(there.node("pilots"));
        assertEquals("NOVA", pilots.name(1));
        assertEquals(0, pilots.careerScore("LOCAL"), "a career from the old profile is still here");
    }

    /**
     * The nodes are emptied, never removed.
     *
     * {@code Pilots} and {@code SaveGames} hold live handles to these nodes and their children.
     * A restore that removed them would leave every menu on screen throwing
     * {@code IllegalStateException} on the next keystroke, which is a game that has to be
     * restarted to be playable -- so the handle taken before the restore is used after it here.
     */
    @Test
    void objectsHoldingTheOldNodesStillWorkAfterwards() {
        Pilots held = Pilots.load(there.node("pilots"));
        held.setName(1, "LOCAL");
        Pilots.load(here.node("pilots")).setName(1, "NOVA");

        Profile.restore(there, Profile.export(here));

        assertEquals("NOVA", held.name(1), "the live handle must see the restored profile");
        held.addCareerScore("NOVA", 10);
        assertEquals(10, held.careerScore("NOVA"), "and must still be writable");
    }

    /**
     * Settings are deliberately not in a profile. Syncing them would be undone anyway -- the live
     * {@code Settings} object writes its cached fields back on exit -- and half of what is in there
     * describes the machine rather than the player.
     */
    @Test
    void settingsAreNotCarried() {
        Settings machine = Settings.load(here);
        machine.setDifficulty(Difficulty.HARD);
        machine.save();

        String profile = Profile.export(here);

        assertFalse(profile.contains(Difficulty.HARD.name()), profile);
    }

    @Test
    void aTruncatedOrForeignProfileChangesNothing() {
        Pilots.load(there.node("pilots")).setName(1, "LOCAL");

        assertFalse(Profile.restore(there, ""));
        assertFalse(Profile.restore(there, null));
        assertFalse(Profile.restore(there, "99\npilots|player1|NOVA"), "a version from the future");
        assertFalse(Profile.restore(there, "1"), "a header and nothing else");
        assertFalse(Profile.restore(there, "1\nsettings|difficulty|HARD"), "no node it will take");

        assertEquals("LOCAL", Pilots.load(there.node("pilots")).name(1),
                "restore parses before it clears, so a refused profile costs nothing");
    }
}
