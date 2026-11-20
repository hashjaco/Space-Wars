package com.hashimjacobs.spacecase.update;

import com.hashimjacobs.spacecase.GameVersion;

/**
 * Release numbers, compared the way people read them.
 *
 * Segment by segment as numbers, so 2.10 comes after 2.9 rather than before it as text would put
 * it, and a missing segment counts as zero, so 2.6 and 2.6.0 are the same release. A leading
 * {@code v}, as on the git tags, is ignored.
 */
public final class Versions {

    private Versions() {
    }

    /** Negative, zero or positive as {@code a} is older than, the same as, or newer than {@code b}. */
    public static int compare(String a, String b) {
        String[] left = segments(a);
        String[] right = segments(b);
        for (int at = 0; at < Math.max(left.length, right.length); at++) {
            long l = at < left.length ? number(left[at]) : 0;
            long r = at < right.length ? number(right[at]) : 0;
            if (l != r) {
                return Long.compare(l, r);
            }
        }
        return 0;
    }

    /**
     * Whether {@code offered} should replace {@code running}. A {@link GameVersion#DEV} build is
     * never offered anything: it was built from source by someone who chose what it is.
     */
    public static boolean isNewer(String offered, String running) {
        if (GameVersion.DEV.equals(running) || offered == null || offered.isBlank()) {
            return false;
        }
        return compare(offered, running) > 0;
    }

    private static String[] segments(String version) {
        String plain = version.trim();
        if (plain.startsWith("v") || plain.startsWith("V")) {
            plain = plain.substring(1);
        }
        return plain.split("\\.");
    }

    /** The leading digits of a segment, so {@code 3-rc1} reads as 3; none at all reads as 0. */
    private static long number(String segment) {
        int end = 0;
        while (end < segment.length() && end < 18 && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Long.parseLong(segment.substring(0, end));
    }
}
