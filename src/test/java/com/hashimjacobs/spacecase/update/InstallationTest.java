package com.hashimjacobs.spacecase.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstallationTest {

    @TempDir
    Path dir;

    @Test
    void aJpackageLauncherIsAnInstallerForItsPlatform() {
        Installation mac = Installation.detect("/Applications/SpaceCase.app/Contents/MacOS/SpaceCase",
                "Mac OS X", "aarch64", null);
        assertEquals(Installation.Kind.JPACKAGE, mac.kind());
        assertEquals("macos", mac.platform());
        assertEquals("macos-intel", Installation.detect("/x", "Mac OS X", "x86_64", null).platform());
        assertEquals("windows", Installation.detect("C:\\SpaceCase\\SpaceCase.exe", "Windows 11", "amd64", null).platform());
        assertEquals("linux", Installation.detect("/opt/spacecase/bin/SpaceCase", "Linux", "amd64", null).platform());
    }

    @Test
    void aJarIsFoundByItsPlatformLabel() {
        Installation jar = Installation.detect(null, "Linux", "amd64", Path.of("/games/space-case-linux.jar"));
        assertEquals(Installation.Kind.JAR, jar.kind());
        assertEquals("jar-linux", jar.platform());
        assertEquals(Path.of("/games/space-case-linux.jar"), jar.location());
    }

    @Test
    void classesInsideAGitCheckoutAreASourceRun() throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Path classes = Files.createDirectories(dir.resolve("target/classes"));
        Installation source = Installation.detect(null, "Linux", "aarch64", classes);
        assertEquals(Installation.Kind.SOURCE, source.kind());
        assertEquals(dir, source.location());
    }

    @Test
    void classesAnywhereElseAreUnknown() throws IOException {
        Path classes = Files.createDirectories(dir.resolve("classes"));
        assertEquals(Installation.Kind.UNKNOWN, Installation.detect(null, "Linux", "amd64", classes).kind());
        assertEquals(Installation.Kind.UNKNOWN, Installation.detect(null, "Linux", "amd64", null).kind());
    }

    @Test
    void anArmBoardIsLinuxArm64() {
        assertEquals("linux-arm64", Installation.platformLabel("Linux", "aarch64"));
    }
}
