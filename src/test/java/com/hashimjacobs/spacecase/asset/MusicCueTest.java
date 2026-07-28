package com.hashimjacobs.spacecase.asset;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicCueTest {

    @Test
    void everyTrackIsReachableFromSomeCue() {
        Set<MusicTrack> reachable = EnumSet.noneOf(MusicTrack.class);
        for (MusicCue cue : MusicCue.values()) {
            reachable.addAll(cue.tracks());
        }
        Set<MusicTrack> all = EnumSet.allOf(MusicTrack.class);
        all.removeAll(reachable);
        assertTrue(all.isEmpty(),
                "these tracks ship in the jar but no cue ever plays them: " + all);
    }

    @Test
    void noTrackIsUsedByTwoCues() {
        Set<MusicTrack> seen = EnumSet.noneOf(MusicTrack.class);
        for (MusicCue cue : MusicCue.values()) {
            for (MusicTrack track : cue.tracks()) {
                assertTrue(seen.add(track), track + " is claimed by more than one cue");
            }
        }
    }

    @Test
    void everyCueHasSomethingToPlay() {
        for (MusicCue cue : MusicCue.values()) {
            assertFalse(cue.tracks().isEmpty(), cue + " has no tracks");
        }
    }

    @Test
    void aCueOnlyEverPicksItsOwnTracks() {
        Random random = new Random(9);
        for (MusicCue cue : MusicCue.values()) {
            for (int i = 0; i < 60; i++) {
                MusicTrack picked = cue.pick(random);
                assertTrue(cue.tracks().contains(picked),
                        cue + " picked " + picked + ", which is not one of its tracks");
            }
        }
    }

    @Test
    void aMultiTrackCueActuallyVaries() {
        Random random = new Random(3);
        Set<MusicTrack> picked = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            picked.add(MusicCue.GAMEPLAY.pick(random));
        }
        assertEquals(MusicCue.GAMEPLAY.tracks().size(), picked.size(),
                "a cue with several tracks should reach all of them across many rounds");
    }

    @Test
    void battleSoundsDifferentFromTheOtherModes() {
        assertEquals(MusicCue.GAMEPLAY, GameMode.SOLO.music());
        assertEquals(MusicCue.GAMEPLAY, GameMode.COOP.music());
        assertNotEquals(GameMode.SOLO.music(), GameMode.BATTLE.music());
    }
}
