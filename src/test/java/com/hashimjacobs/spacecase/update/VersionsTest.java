package com.hashimjacobs.spacecase.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    @Test
    void segmentsCompareAsNumbersNotText() {
        assertTrue(Versions.compare("2.10", "2.9") > 0);
        assertTrue(Versions.compare("2.9", "2.10") < 0);
        assertTrue(Versions.compare("3", "2.99.99") > 0);
    }

    @Test
    void aMissingSegmentIsZeroAndALeadingVIsIgnored() {
        assertEquals(0, Versions.compare("2.6", "2.6.0"));
        assertEquals(0, Versions.compare("v2.7", "2.7"));
        assertTrue(Versions.compare("2.6.1", "2.6") > 0);
    }

    @Test
    void onlyANewerReleaseIsOffered() {
        assertTrue(Versions.isNewer("2.8", "2.7"));
        assertFalse(Versions.isNewer("2.7", "2.7"));
        assertFalse(Versions.isNewer("2.6", "2.7"));
        assertFalse(Versions.isNewer("", "2.7"));
    }

    @Test
    void aDevBuildIsNeverOfferedAnything() {
        assertFalse(Versions.isNewer("99.0", "dev"));
    }
}
