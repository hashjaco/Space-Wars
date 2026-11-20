package com.hashimjacobs.spacecase.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;

import com.hashimjacobs.spacecase.Launcher;
import com.hashimjacobs.spacecase.OnAndroid;

/**
 * How this copy of the game got onto the machine, which decides how it is replaced.
 *
 * @param kind     the shape of the install
 * @param platform the key this copy's download has in {@code update.txt}, such as {@code windows}
 *                 or {@code jar-linux}; empty for a source checkout, which updates through git
 * @param location what an update replaces or restarts: the jpackage launcher, the jar, or the root
 *                 of the git checkout; null on Android and when unknown
 */
public record Installation(Kind kind, String platform, Path location) {

    public enum Kind {
        /** The Android TV apk. Android's own installer replaces it. */
        ANDROID,
        /** A jpackage installer: the dmg, msi or deb. {@code jpackage.app-path} is set. */
        JPACKAGE,
        /** {@code java -jar space-case-<platform>.jar}. */
        JAR,
        /** {@code ./mvnw javafx:run} in a git checkout, as on the Raspberry Pi. */
        SOURCE,
        /** None of those: the game can only point at the release page. */
        UNKNOWN
    }

    /** This process. */
    public static Installation current() {
        if (OnAndroid.ACTIVE) {
            return new Installation(Kind.ANDROID, "androidtv", null);
        }
        return detect(System.getProperty("jpackage.app-path"), System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""), codeSource());
    }

    /**
     * The decision itself, from what {@link #current} reads, so every case can be tested.
     *
     * @param appPath    {@code jpackage.app-path}, or null when not launched by a jpackage launcher
     * @param codeSource where the game's classes were loaded from: a jar, or a classes directory
     */
    static Installation detect(String appPath, String osName, String osArch, Path codeSource) {
        String label = platformLabel(osName, osArch);
        if (appPath != null && !appPath.isBlank()) {
            return new Installation(Kind.JPACKAGE, label, Path.of(appPath));
        }
        if (codeSource != null && codeSource.getFileName() != null
                && codeSource.getFileName().toString().endsWith(".jar")) {
            return new Installation(Kind.JAR, "jar-" + label, codeSource);
        }
        if (codeSource != null) {
            for (Path at = codeSource; at != null; at = at.getParent()) {
                // A directory in a normal clone, a file in a worktree; either is a checkout.
                if (Files.exists(at.resolve(".git")) && Files.exists(at.resolve("pom.xml"))) {
                    return new Installation(Kind.SOURCE, "", at);
                }
            }
        }
        return new Installation(Kind.UNKNOWN, label, null);
    }

    /**
     * The name each platform's downloads carry on a release: {@code macos}, {@code macos-intel},
     * {@code windows}, {@code linux}, and {@code linux-arm64} for an ARM board.
     */
    static String platformLabel(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        boolean arm = osArch.equals("aarch64") || osArch.equals("arm64");
        if (os.startsWith("mac")) {
            return arm ? "macos" : "macos-intel";
        }
        if (os.startsWith("windows")) {
            return "windows";
        }
        return arm ? "linux-arm64" : "linux";
    }

    private static Path codeSource() {
        try {
            CodeSource source = Launcher.class.getProtectionDomain().getCodeSource();
            return source == null || source.getLocation() == null ? null
                    : Path.of(source.getLocation().toURI());
        } catch (Exception | LinkageError unknowable) {
            return null;
        }
    }
}
