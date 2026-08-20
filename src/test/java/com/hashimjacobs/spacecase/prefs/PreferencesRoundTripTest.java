package com.hashimjacobs.spacecase.prefs;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        saved.setGamepadFireButton(1, PadButton.RIGHT_BUMPER);
        saved.setGamepadPauseButton(1, PadButton.BACK);
        saved.save();

        Settings reloaded = Settings.load(scratch);

        assertFalse(reloaded.gamepadEnabled());
        assertEquals(0.45, reloaded.gamepadDeadzone(), 1e-9);
        assertEquals(PadButton.RIGHT_BUMPER, reloaded.gamepadFireButton(1));
        assertEquals(PadButton.BACK, reloaded.gamepadPauseButton(1));
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
        assertEquals(PadButton.A, settings.gamepadFireButton(1));
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

    @Test
    void stickSensitivityIsKeptPerPlayer() {
        Settings settings = Settings.load(scratch);
        settings.setGamepadSensitivity(1, 2.0);
        settings.setGamepadSensitivity(2, 0.5);
        settings.save();

        Settings reloaded = Settings.load(scratch);
        assertEquals(2.0, reloaded.gamepadSensitivity(1), 0.0001);
        assertEquals(0.5, reloaded.gamepadSensitivity(2), 0.0001);
    }

    /** A store written by another version must not hand the curve an exponent of infinity. */
    @Test
    void anOutOfRangeSensitivityClampsRatherThanThrowing() {
        Settings settings = Settings.load(scratch);
        settings.setGamepadSensitivity(1, 99.0);
        settings.setGamepadSensitivity(2, -4.0);

        assertEquals(2.0, settings.gamepadSensitivity(1), 0.0001);
        assertEquals(0.5, settings.gamepadSensitivity(2), 0.0001);
    }

    @Test
    void keyBindingsSurviveAReload() {
        Settings saved = Settings.load(scratch);
        saved.setKey(1, ControlAction.FIRE, KeyCode.Z);
        saved.setKey(2, ControlAction.UP, KeyCode.I);
        saved.save();

        Settings reloaded = Settings.load(scratch);

        assertEquals(KeyCode.Z, reloaded.key(1, ControlAction.FIRE));
        assertEquals(KeyCode.I, reloaded.key(2, ControlAction.UP));
        assertEquals(KeyCode.S, reloaded.key(1, ControlAction.DOWN), "untouched rows keep theirs");
    }

    /** A store written by another build can name a key this JavaFX does not have. */
    @Test
    void anUnknownStoredKeyFallsBackToTheDefault() {
        scratch.put("keyLEFT", "NOT_A_KEY");

        Settings loaded = Settings.load(scratch);

        assertEquals(KeyCode.A, loaded.key(1, ControlAction.LEFT));
    }

    @Test
    void bindingsAreSeparatePerSeatAndResetOneAtATime() {
        Settings settings = Settings.load(scratch);
        settings.setKey(1, ControlAction.FIRE, KeyCode.Z);
        settings.setKey(2, ControlAction.FIRE, KeyCode.M);

        settings.resetKeys(1);

        assertEquals(KeyCode.SHIFT, settings.key(1, ControlAction.FIRE));
        assertEquals(KeyCode.M, settings.key(2, ControlAction.FIRE), "the other seat is untouched");
    }

    /** Two actions on one key means one of them silently stops working, so rebinding refuses it. */
    @Test
    void aKeyAlreadyInUseIsReportedAsAClash() {
        Settings settings = Settings.load(scratch);

        assertEquals(ControlAction.UP, settings.keyClash(1, ControlAction.FIRE, KeyCode.W));
        assertNull(settings.keyClash(1, ControlAction.FIRE, KeyCode.Z));
        assertNull(settings.keyClash(1, ControlAction.UP, KeyCode.W),
                "a row rebound to the key it already has is not a clash");
    }

    // ---- Cleared levels, which is what the universe map gates on --------------------------------

    @Test
    void aClearedLevelSurvivesAReload() {
        SaveGames saved = SaveGames.load(scratch);
        saved.recordClear(GameMode.SOLO, Level.values()[2]);

        SaveGames reloaded = SaveGames.load(scratch);

        assertTrue(reloaded.isCleared(GameMode.SOLO, Level.values()[2]));
        assertFalse(reloaded.isCleared(GameMode.SOLO, Level.values()[3]));
    }

    /** Clearing a level opens exactly the next one, and nothing further along. */
    @Test
    void clearingALevelUnlocksOnlyTheOneAfterIt() {
        SaveGames saves = SaveGames.load(scratch);
        Level[] all = Level.values();

        assertTrue(saves.isUnlocked(GameMode.SOLO, all[0]),
                "the first level of the first galaxy is always open");
        assertFalse(saves.isUnlocked(GameMode.SOLO, all[1]), "nothing is cleared yet");

        saves.recordClear(GameMode.SOLO, all[0]);

        assertTrue(saves.isUnlocked(GameMode.SOLO, all[1]));
        assertFalse(saves.isUnlocked(GameMode.SOLO, all[2]), "one clear does not open two levels");
    }

    @Test
    void theFirstGalaxyIsOpenFromTheStart() {
        SaveGames saves = SaveGames.load(scratch);
        assertTrue(saves.isGalaxyUnlocked(GameMode.SOLO, Galaxy.values()[0]));
    }

    /**
     * A galaxy opens only once the whole of the one before it is done -- all ten, not just its
     * finale, so its middle cannot be skipped by replaying the last level.
     *
     * Written pairwise so it covers each border as that galaxy ships, rather than needing to be
     * revisited. With one galaxy in the game it asserts nothing; the loop body is the test.
     */
    @Test
    void aGalaxyOpensOnlyWhenTheWholeOfTheOneBeforeItIsCleared() {
        Galaxy[] galaxies = Galaxy.values();
        for (int i = 1; i < galaxies.length; i++) {
            SaveGames saves = SaveGames.load(scratch);
            Galaxy previous = galaxies[i - 1];
            List<Level> earlier = previous.levels();

            // Everything but one level of the previous galaxy, including its finale.
            for (Level level : earlier) {
                if (level != earlier.get(3)) {
                    saves.recordClear(GameMode.SOLO, level);
                }
            }
            assertFalse(saves.isGalaxyUnlocked(GameMode.SOLO, galaxies[i]),
                    galaxies[i] + " opened with a hole left in " + previous);

            saves.recordClear(GameMode.SOLO, earlier.get(3));
            assertTrue(saves.isGalaxyUnlocked(GameMode.SOLO, galaxies[i]),
                    galaxies[i] + " should open once " + previous + " is finished");

            scratch.node("saves").remove("cleared/" + GameMode.SOLO.name());
        }
    }

    @Test
    void eachModeKeepsItsOwnProgress() {
        SaveGames saves = SaveGames.load(scratch);

        saves.recordClear(GameMode.SOLO, Level.values()[0]);

        assertTrue(saves.isCleared(GameMode.SOLO, Level.values()[0]));
        assertFalse(saves.isCleared(GameMode.COOP, Level.values()[0]),
                "co-op is a separate campaign, unlike the single shared checkpoint");
    }

    @Test
    void battleModeIsNeverRecorded() {
        SaveGames saves = SaveGames.load(scratch);

        saves.recordClear(GameMode.BATTLE, Level.values()[0]);

        assertEquals(0, saves.clearedMask(GameMode.BATTLE), "battle has no levels to clear");
    }

    @Test
    void aGalaxyCountsHowManyOfItsTenAreDone() {
        SaveGames saves = SaveGames.load(scratch);
        Galaxy first = Galaxy.values()[0];

        assertEquals(0, saves.clearedCount(GameMode.SOLO, first));
        saves.recordClear(GameMode.SOLO, first.levels().get(0));
        saves.recordClear(GameMode.SOLO, first.levels().get(4));

        assertEquals(2, saves.clearedCount(GameMode.SOLO, first));
    }

    /**
     * A player who was mid-campaign before any of this existed keeps what they earned.
     *
     * Their checkpoint is the only record, and it names the level in progress -- written when a level
     * begins -- so everything strictly before it was cleared and the level itself was not.
     */
    @Test
    void aLegacyCheckpointGrantsTheLevelsItImplies() {
        SaveGames writer = SaveGames.load(scratch);
        writer.saveCheckpoint(at(GameMode.SOLO, 4, 1));
        // Wipe the mask so only the checkpoint remains, as it would for a pre-galaxy save.
        scratch.node("saves").remove("cleared/SOLO");

        SaveGames migrated = SaveGames.load(scratch);

        assertEquals(4, migrated.clearedCount(GameMode.SOLO, Galaxy.values()[0]),
                "reaching level index 4 means the four before it were cleared");
        assertTrue(migrated.isCleared(GameMode.SOLO, Level.values()[3]));
        assertFalse(migrated.isCleared(GameMode.SOLO, Level.values()[4]),
                "the level the checkpoint names was in progress, not finished");
    }

    /** Migration only ever adds, so a real clear is never taken away by a stale checkpoint. */
    @Test
    void migrationNeverRemovesProgressAlreadyRecorded() {
        SaveGames saves = SaveGames.load(scratch);
        saves.saveCheckpoint(at(GameMode.SOLO, 1, 1));
        saves.recordClear(GameMode.SOLO, Level.values()[7]);

        SaveGames reloaded = SaveGames.load(scratch);

        assertTrue(reloaded.isCleared(GameMode.SOLO, Level.values()[7]),
                "a shallow checkpoint must not erase a level that was actually cleared");
    }

    @Test
    void unreadableProgressReadsAsNoneRatherThanThrowing() {
        scratch.node("saves").put("cleared/SOLO", "not-hex");

        SaveGames saves = SaveGames.load(scratch);

        assertEquals(0, saves.clearedMask(GameMode.SOLO));
        assertTrue(saves.isUnlocked(GameMode.SOLO, Level.values()[0]),
                "the first level stays reachable whatever the store says");
    }
}
