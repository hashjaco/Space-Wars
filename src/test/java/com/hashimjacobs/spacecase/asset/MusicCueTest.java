package com.hashimjacobs.spacecase.asset;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.Level;

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
        // A cutscene plays its track directly; the ending's has no cue of its own.
        for (com.hashimjacobs.spacecase.scene.Cutscene scene : com.hashimjacobs.spacecase.scene.Cutscene.values()) {
            reachable.add(scene.music());
        }
        Set<MusicTrack> all = EnumSet.allOf(MusicTrack.class);
        all.removeAll(reachable);
        assertTrue(all.isEmpty(),
                "these tracks ship in the jar but nothing ever plays them: " + all);
    }

    @Test
    void heavyTracksOnlyPlayForBosses() {
        Set<MusicTrack> heavy = EnumSet.of(MusicTrack.DEATH_METAL, MusicTrack.DEATH_PUNK, MusicTrack.AEONS_END);
        Set<MusicCue> hard = EnumSet.of(MusicCue.BOSS, MusicCue.AEON_BOSS);
        for (MusicCue cue : MusicCue.values()) {
            if (hard.contains(cue)) {
                assertTrue(heavy.containsAll(cue.tracks()), cue + " should be metal or hard rock only");
            } else {
                for (MusicTrack track : cue.tracks()) {
                    assertFalse(heavy.contains(track), cue + " plays " + track + ", which is saved for bosses");
                }
            }
        }
    }

    @Test
    void everyGalaxyRotatesSeveralTracks() {
        for (Galaxy galaxy : Galaxy.values()) {
            assertTrue(MusicCue.forGalaxy(galaxy).tracks().size() >= 3,
                    galaxy + " should rotate several tracks, not loop one for ten levels");
        }
        assertTrue(MusicCue.BOSS.tracks().size() >= 2, "flagships should not all share one song");
    }

    @Test
    void pickNeverRepeatsWhatItWasToldToAvoid() {
        Random random = new Random(5);
        for (MusicCue cue : MusicCue.values()) {
            MusicTrack last = cue.pick(random);
            for (int i = 0; i < 60; i++) {
                MusicTrack next = cue.pick(random, EnumSet.of(last));
                if (cue.tracks().size() > 1) {
                    assertNotEquals(last, next, cue + " repeated " + last + " back to back");
                }
                assertTrue(cue.tracks().contains(next));
                last = next;
            }
        }
    }

    @Test
    void pickFallsBackWhenEveryTrackIsAvoided() {
        MusicTrack only = MusicCue.AEON_BOSS.pick(new Random(1), EnumSet.allOf(MusicTrack.class));
        assertTrue(MusicCue.AEON_BOSS.tracks().contains(only));
    }

    @Test
    void everyBossGetsTheHeavyCue() {
        for (Boss boss : Boss.values()) {
            assertEquals(boss == Boss.AEON ? MusicCue.AEON_BOSS : MusicCue.BOSS, boss.music(), boss.name());
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
            picked.add(MusicCue.VERDANCE.pick(random));
        }
        assertEquals(MusicCue.VERDANCE.tracks().size(), picked.size(),
                "a cue with several tracks should reach all of them across many rounds");
    }

    @Test
    void battleSoundsDifferentFromTheOtherModes() {
        Level opener = Level.values()[0];
        assertEquals(MusicCue.VERDANCE, GameMode.SOLO.musicFor(opener));
        assertEquals(MusicCue.VERDANCE, GameMode.COOP.musicFor(opener));
        assertNotEquals(GameMode.SOLO.musicFor(opener), GameMode.BATTLE.musicFor(opener));
    }

    @Test
    void everyGalaxySoundsLikeItself() {
        java.util.Set<MusicCue> cues = EnumSet.noneOf(MusicCue.class);
        for (Galaxy galaxy : Galaxy.values()) {
            MusicCue cue = MusicCue.forGalaxy(galaxy);
            assertTrue(cues.add(cue), galaxy + " should not share a cue with another galaxy");
            Level first = Level.values()[galaxy.ordinal() * Galaxy.LEVELS_PER_GALAXY];
            assertEquals(cue, GameMode.SOLO.musicFor(first));
        }
        assertEquals(MusicCue.AEON_BOSS, Boss.AEON.music());
    }
}
