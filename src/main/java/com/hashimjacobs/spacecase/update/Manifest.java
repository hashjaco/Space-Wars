package com.hashimjacobs.spacecase.update;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code update.txt}, the list the release workflow attaches to every release.
 *
 * Plain text, one thing per line, for the same reason {@code net.Cloud} speaks comma-separated
 * fields: the game has no JSON parser and this does not need one. The first word of a line is what
 * it is about:
 *
 * <pre>
 * version 2.7
 * windows    SpaceCase-2.7.msi           3f2a...e9   (platform, file, SHA-256)
 * jar-linux  space-case-linux.jar        91bc...04
 * </pre>
 *
 * Blank lines and lines starting with {@code #} are skipped, and so is any line that is not one of
 * those two shapes, so a later release can add a kind of line without breaking an older game.
 */
public final class Manifest {

    /** One download: which platform it is for, its file name on the release, and its SHA-256. */
    public record Entry(String platform, String file, String sha256) {
    }

    private final String version;
    private final Map<String, Entry> entries;

    private Manifest(String version, Map<String, Entry> entries) {
        this.version = version;
        this.entries = entries;
    }

    /** @throws IllegalArgumentException if there is no {@code version} line */
    public static Manifest parse(String text) {
        String version = null;
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] words = line.split("\\s+");
            if (words.length == 2 && words[0].equals("version")) {
                version = words[1];
            } else if (words.length == 3 && isSha256(words[2]) && !words[1].contains("/")) {
                entries.put(words[0], new Entry(words[0], words[1], words[2].toLowerCase(java.util.Locale.ROOT)));
            }
        }
        if (version == null) {
            throw new IllegalArgumentException("update.txt names no version");
        }
        return new Manifest(version, entries);
    }

    public String version() {
        return version;
    }

    public Optional<Entry> entry(String platform) {
        return Optional.ofNullable(entries.get(platform));
    }

    private static boolean isSha256(String word) {
        return word.length() == 64 && word.chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }
}
