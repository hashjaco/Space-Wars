package com.hashimjacobs.spacecase.prefs;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure string arithmetic: no JavaFX, no Preferences, nothing to clean up. */
class SaveSlotTest {

    private static PlayerShip.Progress progress(int score) {
        return new PlayerShip.Progress(80, 2, score, 31, 7, 420, 300, 55);
    }

    @Test
    void aSoloRunRoundTripsEveryField() {
        SaveSlot saved = new SaveSlot(GameMode.SOLO, Level.values()[3], 12, 2,
                List.of(progress(9400)));

        SaveSlot back = SaveSlot.decode(saved.encode()).orElseThrow();

        assertEquals(GameMode.SOLO, back.mode());
        assertEquals(Level.values()[3], back.level());
        assertEquals(12, back.wavesSurvived());
        assertEquals(2, back.loop());
        assertEquals(1, back.players().size());
        assertEquals(progress(9400), back.players().get(0));
    }

    @Test
    void aCoopRunKeepsBothPilotsInOrder() {
        SaveSlot saved = new SaveSlot(GameMode.COOP, Level.values()[0], 3, 1,
                List.of(progress(100), progress(200)));

        SaveSlot back = SaveSlot.decode(saved.encode()).orElseThrow();

        assertEquals(2, back.players().size());
        assertEquals(100, back.players().get(0).score());
        assertEquals(200, back.players().get(1).score());
    }

    /**
     * A preferences value is user-writable and can be left half-written by a crash. None of these
     * may throw, and none may produce a run -- unlike a loadout there is no valid default here.
     */
    @Test
    void anythingUnrecognisableReadsAsNoSaveRatherThanThrowing() {
        String[] rubbish = {
                null, "", "   ", "nonsense",
                "1,SOLO,3",                              // truncated header
                "9,SOLO,3,1,1",                          // wrong version
                "1,TIME_ATTACK,3,1,1",                   // a mode this build does not have
                "1,SOLO,99,1,1",                         // level past the end
                "1,SOLO,-1,1,1",                         // level before the start
                "1,SOLO,3,1,1,80,2,900",                 // ragged player group
                "1,SOLO,3,1,1,x,2,9,1,1,1,1,1",          // non-numeric
                "1,,,,",
        };
        for (String code : rubbish) {
            Optional<SaveSlot> read = SaveSlot.decode(code);
            assertTrue(read.isEmpty(), "should not have decoded: " + code);
        }
    }

    @Test
    void aReplayCarriesNoPlayersSoNothingIsRestored() {
        SaveSlot replay = SaveSlot.forReplay(GameMode.SOLO, Level.values()[5]);

        assertTrue(replay.players().isEmpty());
        assertEquals(1, replay.loop());
        assertEquals(Level.values()[5], replay.level());
    }

    @Test
    void aRunDescribesItselfForAMenuRow() {
        SaveSlot saved = new SaveSlot(GameMode.COOP, Level.values()[4], 9, 1,
                List.of(progress(400), progress(12400)));

        String row = saved.describe();

        assertTrue(row.contains("Co-op"), row);
        assertTrue(row.contains(Level.values()[4].label()), row);
        assertTrue(row.contains("12400"), "should show the leading score, got: " + row);
    }

    @Test
    void anEncodedRunStaysWellInsideAPreferencesValue() {
        SaveSlot twoPlayers = new SaveSlot(GameMode.COOP, Level.values()[7], 999, 9,
                List.of(progress(999999), progress(999999)));

        assertTrue(twoPlayers.encode().length() < 512,
                "encoded to " + twoPlayers.encode().length() + " chars");
        assertFalse(twoPlayers.encode().contains("\n"));
    }

    /**
     * The migration that matters: a save written before the campaign grew still resumes.
     *
     * Version 1 stored the level as an ordinal, version 2 stores its name. A v1 record has to keep
     * naming the same level it always did -- getting this wrong would not fail loudly, it would
     * quietly resume people somewhere else in the campaign.
     */
    @Test
    void aVersionOneRecordStillNamesTheLevelItAlwaysDid() {
        String legacy = "1,SOLO,4,7,1";

        SaveSlot back = SaveSlot.decode(legacy).orElseThrow();

        assertEquals(Level.values()[4], back.level(), "ordinal 4 has to stay the fifth level");
        assertEquals(GameMode.SOLO, back.mode());
        assertEquals(7, back.wavesSurvived());
        assertTrue(back.players().isEmpty());
    }

    @Test
    void aCurrentRecordStoresTheLevelByName() {
        Level level = Level.values()[6];
        SaveSlot saved = new SaveSlot(GameMode.SOLO, level, 4, 1, List.of());

        assertTrue(saved.encode().contains(level.name()),
                "the level should be named, not numbered: " + saved.encode());
        assertEquals(level, SaveSlot.decode(saved.encode()).orElseThrow().level());
    }

    /**
     * A name this build does not have reads as no save, not as level one.
     *
     * The failure this prevents: a save naming a level that was renamed or removed resolving to
     * whatever happened to be first, and dropping the player into the wrong galaxy.
     */
    @Test
    void aLevelNameThisBuildDoesNotHaveReadsAsNoSave() {
        assertEquals(Optional.empty(), SaveSlot.decode("2,SOLO,ATLANTIS_SHELF,3,1"));
        assertEquals(Optional.empty(), SaveSlot.decode("2,SOLO,,3,1"));
        assertEquals(Optional.empty(), SaveSlot.decode("2,SOLO,4,3,1"),
                "a bare ordinal is not a valid version 2 record");
    }

    /** A version from a build newer than this one is not guessed at. */
    @Test
    void anUnknownVersionReadsAsNoSave() {
        assertEquals(Optional.empty(), SaveSlot.decode("3,SOLO,ORBITAL_APPROACH,3,1"));
    }

    @Test
    void namingLevelsKeepsTheRecordWellInsideAPreferencesValue() {
        // Names are longer than ordinals, so the headroom is worth re-checking against the longest
        // label in the game rather than assuming.
        Level longest = Level.values()[0];
        for (Level level : Level.values()) {
            if (level.name().length() > longest.name().length()) {
                longest = level;
            }
        }
        SaveSlot twoPlayers = new SaveSlot(GameMode.COOP, longest, 999, 9,
                List.of(progress(999999), progress(999999)));

        assertTrue(twoPlayers.encode().length() < 512,
                "encoded to " + twoPlayers.encode().length() + " chars");
    }
}
