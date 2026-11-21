package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A pilot's help points: kept per pilot, only ever added to, and apart from credits and career. */
class PilotsHelpTest {

    private final Preferences scratch =
            Preferences.userRoot().node("space-case-help-test-" + System.nanoTime());

    @AfterEach
    void removeScratch() throws BackingStoreException {
        scratch.removeNode();
    }

    @Test
    void aPilotStartsWithNoneAndEachLevelHelpedAdds() {
        Pilots pilots = Pilots.load(scratch);

        assertEquals(0, pilots.helpPoints("ACE"));
        assertEquals(100, pilots.addHelpPoints("ACE", 100));
        assertEquals(200, pilots.addHelpPoints("ACE", 100));
        assertEquals(0, pilots.helpPoints("NOVA"), "another pilot's help is their own");
    }

    @Test
    void helpPointsSurviveAReloadAndTouchNothingElse() {
        Pilots.load(scratch).addHelpPoints("ACE", 300);

        Pilots again = Pilots.load(scratch);

        assertEquals(300, again.helpPoints("ACE"));
        assertEquals(0, again.credits("ACE"));
        assertEquals(0, again.careerScore("ACE"));
    }

    @Test
    void aNegativeAmountTakesNothingAway() {
        Pilots pilots = Pilots.load(scratch);
        pilots.addHelpPoints("ACE", 100);

        assertEquals(100, pilots.addHelpPoints("ACE", -50));
    }
}
