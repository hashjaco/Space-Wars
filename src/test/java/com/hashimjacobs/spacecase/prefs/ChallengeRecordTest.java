package com.hashimjacobs.spacecase.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.Challenge;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;

class ChallengeRecordTest {

    private final Preferences scratch =
            Preferences.userRoot().node("space-case-challenge-test-" + System.nanoTime());

    @AfterEach
    void removeScratch() throws BackingStoreException {
        scratch.removeNode();
    }

    @Test
    void onlyNewChallengesArePaidAndTheyAddUpToOneHundredPercent() {
        SaveGames saves = SaveGames.load(scratch);
        Level level = Level.ORBITAL_APPROACH;
        int first = Challenge.CLOCK.bit() | Challenge.CORE_2.bit();
        assertEquals(first, saves.recordChallenges(level, first));
        assertEquals(Challenge.S_RANK.bit(), saves.recordChallenges(level, first | Challenge.S_RANK.bit()));
        assertEquals(0, saves.recordChallenges(level, first));
        assertEquals(200, Challenge.creditsFor(first));

        assertEquals(3 / 7.0, saves.completion(level), 1e-9);
        saves.recordClear(GameMode.COOP, level);
        saves.recordChallenges(level, Challenge.ALL);
        assertEquals(1.0, saves.completion(level), 1e-9);
        assertEquals(10, saves.percent(Galaxy.VERDANCE));
        assertEquals(2, saves.percent(null));
    }
}
