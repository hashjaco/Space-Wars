package com.hashimjacobs.spacecase.asset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the licence.
 *
 * Publishing under MIT asserts the right to license everything in the repository, so every bundled
 * asset must have a recorded origin and none of the third-party files that were removed may come
 * back. These are cheap checks that fail the build rather than a promise in a document.
 */
class AssetProvenanceTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path MANIFEST = Path.of("ASSETS.md");

    /** Files whose licence could not be established. None of these may return. */
    private static final List<String> BANNED = List.of(
            "Katdrop-Call-The-Cops.wav",
            "ZHU-Nero-Dreams(Tank Trim).wav",
            "TITANIC-FLUTE-FAIL-Sound-Effect-Best-Sound-Effects-TV.wav",
            "redbull.png",
            "invader-animated-red.gif",
            "asteroid.png",
            "aNuttaAsteroid.png",
            "healthPU.png",
            "shield.png",
            "extraLife.gif",
            "enemyShip2.png",
            "enemyShip3.png",
            "bossShip1.jpg",
            "spaceBackground.gif",
            "Lugosi.ttf");

    @Test
    void everyBundledAssetIsAccountedForInTheManifest() throws IOException {
        String manifest = Files.readString(MANIFEST);
        List<String> undocumented = new ArrayList<>();

        for (Path file : bundledFiles()) {
            Path relative = RESOURCES.relativize(file);
            // Numbered animation frames are documented as a range, so credit the directory instead.
            String needle = relative.getNameCount() > 2
                    ? relative.getName(1).toString()
                    : file.getFileName().toString();
            if (!manifest.contains(needle)) {
                undocumented.add(relative.toString());
            }
        }

        assertTrue(undocumented.isEmpty(),
                "these assets are bundled but absent from ASSETS.md: " + undocumented);
    }

    @Test
    void noRemovedThirdPartyAssetHasReturned() throws IOException {
        List<String> present = new ArrayList<>();
        for (Path file : bundledFiles()) {
            String name = file.getFileName().toString();
            if (BANNED.contains(name)) {
                present.add(name);
            }
        }
        assertTrue(present.isEmpty(),
                "third-party assets are back in the repository: " + present);
    }

    @Test
    void everySpriteConstantPointsAtAFileThatExists() {
        List<String> missing = new ArrayList<>();
        for (Sprite sprite : Sprite.values()) {
            Path file = RESOURCES.resolve(sprite.resourcePath().substring(1));
            if (!Files.exists(file)) {
                missing.add(sprite + " -> " + sprite.resourcePath());
            }
        }
        assertTrue(missing.isEmpty(), "sprites reference files that do not exist: " + missing);
    }

    @Test
    void everySoundConstantPointsAtAFileThatExists() {
        List<String> missing = new ArrayList<>();
        for (SoundFx effect : SoundFx.values()) {
            Path file = RESOURCES.resolve(effect.resourcePath().substring(1));
            if (!Files.exists(file)) {
                missing.add(effect + " -> " + effect.resourcePath());
            }
        }
        for (MusicTrack track : MusicTrack.values()) {
            Path file = RESOURCES.resolve(track.resourcePath().substring(1));
            if (!Files.exists(file)) {
                missing.add(track + " -> " + track.resourcePath());
            }
        }
        assertTrue(missing.isEmpty(), "audio references files that do not exist: " + missing);
    }

    /**
     * The declared length of every effect has to match the file it names.
     *
     * {@code VoiceLimiter} frees a slot on the strength of {@link SoundFx#seconds()}, so a sample
     * re-cut without updating the number would silently break the ceiling that keeps native media
     * players from piling up and hanging the window. Nothing else here would notice: the manifest
     * check matches filenames, never contents.
     *
     * Read through {@code javax.sound.sampled}, which is in the JDK, so this stays runnable in a
     * suite that never starts the JavaFX toolkit. It cannot decode MP3, so the one MP3 effect is
     * checked for existence only, above.
     */
    @Test
    void everySoundConstantDeclaresTheLengthOfItsFile() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (SoundFx effect : SoundFx.values()) {
            if (effect.resourcePath().endsWith(".mp3")) {
                continue;
            }
            Path file = RESOURCES.resolve(effect.resourcePath().substring(1));
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());
            double actual = format.getFrameLength() / format.getFormat().getFrameRate();
            if (Math.abs(actual - effect.seconds()) > 0.05) {
                wrong.add(String.format("%s declares %.2fs but %s is %.2fs",
                        effect, effect.seconds(), file.getFileName(), actual));
            }
        }
        assertTrue(wrong.isEmpty(), "declared sound lengths are out of date: " + wrong);
    }

    @Test
    void everyExplosionFrameExists() {
        List<String> missing = new ArrayList<>();
        for (Explosion explosion : Explosion.values()) {
            for (int frame = 1; frame <= explosion.frameCount(); frame++) {
                Path file = RESOURCES.resolve(explosion.framePath(frame).substring(1));
                if (!Files.exists(file)) {
                    missing.add(explosion.framePath(frame));
                }
            }
        }
        assertTrue(missing.isEmpty(), "explosion frames are missing: " + missing);
    }

    @Test
    void everyBossFrameExists() {
        List<String> missing = new ArrayList<>();
        for (BossArt art : BossArt.values()) {
            for (int frame = 1; frame <= art.frameCount(); frame++) {
                Path file = RESOURCES.resolve(art.framePath(frame).substring(1));
                if (!Files.exists(file)) {
                    missing.add(art.framePath(frame));
                }
            }
        }
        assertTrue(missing.isEmpty(), "boss animation frames are missing: " + missing);
    }

    @Test
    void noFontIsBundled() throws IOException {
        List<String> fonts = new ArrayList<>();
        for (Path file : bundledFiles()) {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".woff2")) {
                fonts.add(file.getFileName().toString());
            }
        }
        assertFalse(Files.exists(RESOURCES.resolve("fonts")) && !fonts.isEmpty(),
                "bundling a font redistributes it; reference an installed family instead: " + fonts);
    }

    private static List<Path> bundledFiles() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            List<Path> collected = files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .toList();
            return collected;
        }
    }
}
