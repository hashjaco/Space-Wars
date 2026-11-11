package com.hashimjacobs.spacecase.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.mode.Galaxy;

class CutsceneTest {

    /**
     * Every scene's timeline (built by tools/cutscenes/mix.py) is in order and names only shots that
     * have a still, and there is no still or clip past the last shot it names.
     */
    @Test
    void timelinesAreOrderedAndEveryShotHasItsStill() {
        for (Cutscene scene : Cutscene.values()) {
            Cutscene.Timeline timeline = scene.timeline();
            assertFalse(timeline.lines().isEmpty(), scene + " has no lines");
            assertNotNull(scene.resource("scene.mp3"), scene + " has no scene.mp3");

            double last = -1;
            int highest = 0;
            for (Cutscene.Shot shot : timeline.shots()) {
                assertTrue(shot.start() >= last, scene + " shot " + shot.number() + " starts out of order");
                last = shot.start();
                highest = Math.max(highest, shot.number());
                assertNotNull(scene.resource(shot.number(), "jpg"), scene + " shot " + shot.number());
            }
            last = -1;
            for (Cutscene.Line line : timeline.lines()) {
                assertTrue(line.start() >= last, scene + " line out of order: " + line.text());
                last = line.start();
                assertTrue(line.start() + line.length() <= timeline.end(), scene + " ends mid-line");
            }
            assertNull(scene.resource(highest + 1, "jpg"), scene + " has a still no shot uses");
            assertNull(scene.resource(highest + 1, "mp4"), scene + " has a clip no shot uses");
        }
    }

    /** A shipped build has no clips in the jar: a shot plays the fetched one, or none at all. */
    @Test
    void aClipComesFromTheCacheWhenItIsNotBundled(@org.junit.jupiter.api.io.TempDir java.nio.file.Path cache)
            throws java.io.IOException {
        System.setProperty("cutscenes.cache", cache.toString());
        try {
            assertNull(Cutscene.INTRO.clip(999), "no clip anywhere is a still, not a crash");
            java.nio.file.Files.write(cache.resolve("intro-999.mp4"), new byte[] {0});
            assertEquals("file", Cutscene.INTRO.clip(999).getProtocol());
        } finally {
            System.clearProperty("cutscenes.cache");
        }
    }

    @Test
    void eachGalaxyBorderHasItsSceneAndTheLastIsTheEnding() {
        for (Galaxy galaxy : Galaxy.values()) {
            Cutscene scene = Cutscene.after(galaxy);
            assertEquals(galaxy, scene.unlockedBy());
        }
        assertEquals(Cutscene.ENDING, Cutscene.after(Galaxy.NULL));
    }
}
