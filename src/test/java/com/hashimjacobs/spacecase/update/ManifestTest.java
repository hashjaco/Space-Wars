package com.hashimjacobs.spacecase.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTest {

    private static final String SHA = "02ce28785c075f6fa71f4a1bc93c323d45273a664bbc50d6cee4ea208c5bf7b7";

    @Test
    void readsTheVersionAndEachPlatformsFile() {
        Manifest manifest = Manifest.parse("version 2.8\n"
                + "windows SpaceCase-2.8.msi " + SHA + "\r\n"
                + "jar-linux   space-case-linux.jar   " + SHA.toUpperCase() + "\n");
        assertEquals("2.8", manifest.version());
        assertEquals("SpaceCase-2.8.msi", manifest.entry("windows").orElseThrow().file());
        assertEquals(SHA, manifest.entry("jar-linux").orElseThrow().sha256(), "checksums are kept lower case");
    }

    @Test
    void aPlatformWithNoLineHasNoEntry() {
        assertTrue(Manifest.parse("version 2.8\n").entry("macos").isEmpty());
    }

    @Test
    void linesItDoesNotKnowAreSkipped() {
        Manifest manifest = Manifest.parse("# comment\n\nversion 2.8\n"
                + "notes something new\n"
                + "macos SpaceCase.dmg not-a-checksum\n"
                + "linux ../../etc/passwd " + SHA + "\n"
                + "future-kind a b c d\n");
        assertTrue(manifest.entry("macos").isEmpty(), "a bad checksum is no entry");
        assertTrue(manifest.entry("linux").isEmpty(), "a file name with a path in it is no entry");
    }

    @Test
    void aListWithNoVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse("windows a.msi " + SHA));
        assertThrows(IllegalArgumentException.class, () -> Manifest.parse("<html>Not Found</html>"));
    }
}
