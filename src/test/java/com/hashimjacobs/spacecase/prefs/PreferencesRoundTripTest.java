package com.hashimjacobs.spacecase.prefs;

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
    void gamepadSettingsSurviveAReload() {
        Settings saved = Settings.load(scratch);
        saved.setGamepadEnabled(false);
        saved.setGamepadDeadzone(0.45);
        saved.setGamepadFireButton(PadButton.RIGHT_BUMPER);
        saved.setGamepadPauseButton(PadButton.BACK);
        saved.save();

        Settings reloaded = Settings.load(scratch);

        assertFalse(reloaded.gamepadEnabled());
        assertEquals(0.45, reloaded.gamepadDeadzone(), 1e-9);
        assertEquals(PadButton.RIGHT_BUMPER, reloaded.gamepadFireButton());
        assertEquals(PadButton.BACK, reloaded.gamepadPauseButton());
    }

    @Test
    void theDeadzoneIsClampedToAUsableRange() {
        Settings settings = Settings.load(scratch);

        settings.setGamepadDeadzone(0);
        assertTrue(settings.gamepadDeadzone() > 0, "a zero deadzone reads a resting stick as held");

        settings.setGamepadDeadzone(1.0);
        assertTrue(settings.gamepadDeadzone() < 1.0, "the stick must still be able to reach it");
    }

    @Test
    void anUnknownStoredPadButtonFallsBackToTheDefault() {
        scratch.put("gamepadFireButton", "PADDLE_7");
        Settings settings = Settings.load(scratch);
        assertEquals(PadButton.A, settings.gamepadFireButton());
    }

    @Test
    void padButtonsCycleThroughEveryChoiceAndWrap() {
        assertEquals(PadButton.B, PadButton.A.next());
        PadButton[] all = PadButton.values();
        assertEquals(PadButton.A, all[all.length - 1].next(), "cycling wraps around");
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

    @Test
    void garageCreditsSurviveAReload() {
        Pilots saved = Pilots.load(scratch);
        saved.addCredits("ACE", 250);
        saved.addCredits("ACE", 100);

        Pilots reloaded = Pilots.load(scratch);

        assertEquals(350, reloaded.credits("ACE"));
        assertEquals(0, reloaded.credits("NOBODY"), "an unknown pilot starts broke, not negative");
    }

    @Test
    void aLoadoutCodeSurvivesAReload() {
        Pilots saved = Pilots.load(scratch);
        saved.setLoadoutCode("ACE", "1,2,0,1,0,3,4,2,17,5");

        Pilots reloaded = Pilots.load(scratch);

        assertEquals("1,2,0,1,0,3,4,2,17,5", reloaded.loadoutCode("ACE"));
        assertEquals("", reloaded.loadoutCode("NOBODY"));
    }

    @Test
    void creditsAreKeptApartFromCareerScore() {
        Pilots pilots = Pilots.load(scratch);
        pilots.addCareerScore("ACE", 5000);
        pilots.addCredits("ACE", 200);

        pilots.setCredits("ACE", 0);

        assertEquals(0, pilots.credits("ACE"));
        assertEquals(5000, pilots.careerScore("ACE"),
                "spending in the garage must not cost a pilot their rank");
    }

    @Test
    void aBalanceNeverGoesNegative() {
        Pilots pilots = Pilots.load(scratch);

        pilots.setCredits("ACE", -500);

        assertEquals(0, pilots.credits("ACE"));
    }

    private static SaveSlot at(GameMode mode, int levelOrdinal, int loop) {
        return new SaveSlot(mode, Level.values()[levelOrdinal], 5, loop,
                java.util.List.of(new PlayerShip.Progress(90, 3, 500, 10, 2, 100, 70, 20)));
    }

    @Test
    void aCheckpointSurvivesAReload() {
        SaveGames saved = SaveGames.load(scratch);
        saved.saveCheckpoint(at(GameMode.SOLO, 3, 1));

        SaveGames reloaded = SaveGames.load(scratch);

        SaveSlot back = reloaded.checkpoint().orElseThrow();
        assertEquals(Level.values()[3], back.level());
        assertEquals(GameMode.SOLO, back.mode());
    }

    /** The rule that lets Level Select replay an early level without erasing a late Continue. */
    @Test
    void aCheckpointNeverMovesBackwardsWithinTheSameCampaign() {
        SaveGames saves = SaveGames.load(scratch);
        saves.saveCheckpoint(at(GameMode.SOLO, 6, 1));

        saves.saveCheckpoint(at(GameMode.SOLO, 1, 1));

        assertEquals(Level.values()[6], saves.checkpoint().orElseThrow().level(),
                "replaying an early level must not overwrite later progress");
    }

    @Test
    void aCheckpointMovesForwardAndAcrossModes() {
        SaveGames saves = SaveGames.load(scratch);
        saves.saveCheckpoint(at(GameMode.SOLO, 2, 1));

        saves.saveCheckpoint(at(GameMode.SOLO, 3, 1));
        assertEquals(Level.values()[3], saves.checkpoint().orElseThrow().level());

        // A different mode is a different campaign, so it takes the slot regardless of depth.
        saves.saveCheckpoint(at(GameMode.COOP, 0, 1));
        assertEquals(GameMode.COOP, saves.checkpoint().orElseThrow().mode());
    }

    @Test
    void aLaterLoopOutranksADeeperFirstPass() {
        SaveGames saves = SaveGames.load(scratch);
        saves.saveCheckpoint(at(GameMode.SOLO, Level.values().length - 1, 1));

        saves.saveCheckpoint(at(GameMode.SOLO, 0, 2));

        assertEquals(2, saves.checkpoint().orElseThrow().loop());
    }

    @Test
    void battleModeIsNeverCheckpointed() {
        SaveGames saves = SaveGames.load(scratch);

        saves.saveCheckpoint(at(GameMode.BATTLE, 0, 1));

        assertTrue(saves.checkpoint().isEmpty(), "battle has no levels to sit between");
    }

    @Test
    void manualSlotsAreIndependentAndOverwriteFreely() {
        SaveGames saves = SaveGames.load(scratch);

        saves.save(1, at(GameMode.SOLO, 2, 1));
        saves.save(3, at(GameMode.COOP, 5, 1));

        assertEquals(Level.values()[2], saves.slot(1).orElseThrow().level());
        assertTrue(saves.slot(2).isEmpty(), "an unwritten slot reads empty");
        assertEquals(GameMode.COOP, saves.slot(3).orElseThrow().mode());

        // Unlike the checkpoint, a manual slot has no no-regress rule: the player asked for it.
        saves.save(1, at(GameMode.SOLO, 0, 1));
        assertEquals(Level.values()[0], saves.slot(1).orElseThrow().level());
    }

    @Test
    void anOutOfRangeSlotIsIgnoredRatherThanThrowing() {
        SaveGames saves = SaveGames.load(scratch);

        saves.save(0, at(GameMode.SOLO, 1, 1));
        saves.save(99, at(GameMode.SOLO, 1, 1));

        assertTrue(saves.slot(0).isEmpty());
        assertTrue(saves.slot(99).isEmpty());
    }

    @Test
    void perLevelBestsAreSeparateFromEachOtherAndFromTheRunTotal() {
        HighScores scores = HighScores.load(scratch);

        scores.submit(GameMode.SOLO, 50_000);
        scores.submit(GameMode.SOLO, Level.values()[1], 4000);
        scores.submit(GameMode.SOLO, Level.values()[2], 7000);

        assertEquals(4000, scores.best(GameMode.SOLO, Level.values()[1]));
        assertEquals(7000, scores.best(GameMode.SOLO, Level.values()[2]));
        assertEquals(0, scores.best(GameMode.COOP, Level.values()[1]), "modes are separate");
        assertEquals(50_000, scores.best(GameMode.SOLO), "the run total is untouched");
    }

    @Test
    void aWorseLevelScoreDoesNotReplaceTheBest() {
        HighScores scores = HighScores.load(scratch);
        scores.submit(GameMode.SOLO, Level.values()[0], 9000);

        assertFalse(scores.submit(GameMode.SOLO, Level.values()[0], 8000));
        assertEquals(9000, scores.best(GameMode.SOLO, Level.values()[0]));
    }

    @Test
    void twoPilotsKeepSeparateGarages() {
        Pilots pilots = Pilots.load(scratch);
        pilots.addCredits("ONE", 300);
        pilots.setLoadoutCode("ONE", "1,4,0,0,0,0,0,0,3,1");

        assertEquals(0, pilots.credits("TWO"));
        assertEquals("", pilots.loadoutCode("TWO"));
        assertEquals(300, pilots.credits("ONE"));
    }
}
