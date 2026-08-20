package com.hashimjacobs.spacecase.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller database has to be on the classpath, at the exact path Jamepad looks for.
 *
 * Jamepad's ControllerManager loads "/gamecontrollerdb.txt" as a classpath resource. Without it SDL
 * falls back to the handful of mappings compiled into it, and a pad outside that handful is never
 * opened -- so it reports as disconnected and the game silently ignores it. That is quiet enough to
 * have shipped once already. This is the check that would have caught it, and it needs no
 * controller and no JavaFX toolkit to run.
 */
class GamepadDatabaseTest {

    /** Where Jamepad's ControllerManager looks. Its default mappingsPath, verbatim. */
    private static final String RESOURCE = "/gamecontrollerdb.txt";

    /** The upstream database carries thousands; a few hundred would mean a truncated download. */
    private static final int EXPECTED_AT_LEAST = 1000;

    @Test
    void theControllerDatabaseIsOnTheClasspathAndEveryLineIsAMapping() throws Exception {
        List<String> mappings = new ArrayList<>();
        try (InputStream source = GamepadDatabaseTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(source, RESOURCE + " is not on the classpath, so SDL falls back to its"
                    + " built-in mappings and most controllers go unrecognised");
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
            for (String line : reader.lines().toList()) {
                String trimmed = line.strip();
                // Comments carry the upstream zlib notice, so they are expected, not stripped.
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    mappings.add(trimmed);
                }
            }
        }

        assertTrue(mappings.size() >= EXPECTED_AT_LEAST,
                RESOURCE + " holds only " + mappings.size() + " mappings, expected at least "
                        + EXPECTED_AT_LEAST + " -- a truncated or empty download");

        List<String> malformed = new ArrayList<>();
        for (String mapping : mappings) {
            // guid,name,at least one binding. SDL drops a line failing this silently, so a mangled
            // file would otherwise surface as a pad that does nothing rather than as a test failure.
            String[] fields = mapping.split(",");
            boolean guid = fields[0].matches("[0-9a-fA-F]{32}") || fields[0].equals("xinput");
            if (fields.length < 3 || !guid) {
                malformed.add(mapping.length() > 60 ? mapping.substring(0, 60) + "..." : mapping);
            }
        }
        assertTrue(malformed.isEmpty(), "not valid SDL controller mappings: " + malformed);
    }
}
