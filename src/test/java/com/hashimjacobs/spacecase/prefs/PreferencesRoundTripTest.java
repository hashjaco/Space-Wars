package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings and high scores survive a restart. Uses a throwaway node, never the real one. */
class PreferencesRoundTripTest {

    private final Preferences scratch =
            Preferences.userRoot().node("space-case-test-" + System.nanoTime());

    @AfterEach
    void removeScratchNode() throws BackingStoreException {
        scratch.removeNode();
        scratch.flush();
    }

    @Test
    void settingsSurviveAReload() {
        Settings saved = Settings.load(scratch);
        saved.setMusicVolume(0.3);
        saved.setSfxVolume(0.9);
        saved.setDifficulty(Difficulty.HARD);
        saved.save();

        Settings reloaded = Settings.load(scratch);

        assertEquals(0.3, reloaded.musicVolume(), 1e-9);
        assertEquals(0.9, reloaded.sfxVolume(), 1e-9);
        assertEquals(Difficulty.HARD, reloaded.difficulty());
    }

    @Test
    void volumesAreClampedToZeroThroughOne() {
        Settings settings = Settings.load(scratch);
        settings.setMusicVolume(4.2);
        assertEquals(1.0, settings.musicVolume(), 1e-9);
        settings.setSfxVolume(-3);
        assertEquals(0.0, settings.sfxVolume(), 1e-9);
    }

    @Test
    void anUnknownStoredDifficultyFallsBackToNormal() {
        scratch.put("difficulty", "IMPOSSIBLE");
        Settings settings = Settings.load(scratch);
        assertEquals(Difficulty.NORMAL, settings.difficulty());
    }

    @Test
    void difficultyCyclesThroughEveryPreset() {
        assertEquals(Difficulty.NORMAL, Difficulty.EASY.next());
        assertEquals(Difficulty.HARD, Difficulty.NORMAL.next());
        assertEquals(Difficulty.EASY, Difficulty.HARD.next(), "cycling wraps around");
    }

    @Test
    void onlyABetterScoreIsRecorded() {
        HighScores scores = HighScores.load(scratch);
        assertEquals(0, scores.best(GameMode.SOLO));

        assertTrue(scores.submit(GameMode.SOLO, 500));
        assertEquals(500, scores.best(GameMode.SOLO));

        assertFalse(scores.submit(GameMode.SOLO, 200), "a worse run is not a record");
        assertEquals(500, scores.best(GameMode.SOLO));

        assertTrue(scores.submit(GameMode.SOLO, 900));
        assertEquals(900, scores.best(GameMode.SOLO));
    }

    @Test
    void scoresAreTrackedPerMode() {
        HighScores scores = HighScores.load(scratch);
        scores.submit(GameMode.SOLO, 400);

        assertEquals(0, scores.best(GameMode.COOP), "modes do not share a best score");
        assertEquals(400, scores.best(GameMode.SOLO));
    }

    @Test
    void highScoresSurviveAReload() {
        HighScores saved = HighScores.load(scratch);
        saved.submit(GameMode.BATTLE, 1234);

        HighScores reloaded = HighScores.load(scratch);

        assertEquals(1234, reloaded.best(GameMode.BATTLE));
    }
}
