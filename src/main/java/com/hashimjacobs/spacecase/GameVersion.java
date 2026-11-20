package com.hashimjacobs.spacecase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version this build of the game is, as pom.xml says.
 *
 * Maven writes {@code spacecase/version.properties} from the pom at build time (it is the one
 * filtered resource), so the About page and the update check can no longer drift from the release
 * they came in. A run that skipped the resource phase -- an IDE compiling the sources alone -- has
 * no such file and reports {@link #DEV}, which the update check treats as never out of date.
 */
public final class GameVersion {

    /** What a build without version.properties calls itself. */
    public static final String DEV = "dev";

    private static final Properties FILE = read();

    private GameVersion() {
    }

    /** The pom's {@code <version>}, such as {@code 2.7}, or {@link #DEV}. */
    public static String current() {
        return value("version", DEV);
    }

    /** The day the build was made, such as {@code November 20, 2026}, or empty. */
    public static String date() {
        return value("date", "");
    }

    private static String value(String key, String fallback) {
        String value = FILE.getProperty(key, "").trim();
        // An unfiltered copy still says ${...}: treat it as missing rather than show it.
        return value.isEmpty() || value.startsWith("${") ? fallback : value;
    }

    private static Properties read() {
        Properties properties = new Properties();
        try (InputStream in = GameVersion.class.getResourceAsStream("/spacecase/version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException unreadable) {
            System.err.println("[version] could not read version.properties: " + unreadable);
        }
        return properties;
    }
}
