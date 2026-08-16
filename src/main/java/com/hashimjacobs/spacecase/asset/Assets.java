package com.hashimjacobs.spacecase.asset;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

/**
 * Decodes and caches every image the game draws.
 *
 * Everything resolves through the classloader, so the game no longer depends on being launched
 * from the project directory and can run from a packaged jar.
 *
 * Most sprites are decoded once at startup. Backdrop layers and boss frames are not: there are
 * twenty-four arena-sized layers and only three are ever on screen, so they fault in on request and
 * {@link #preload} warms the next level's set while the game covers the wait.
 */
public final class Assets {

    private static final Map<Sprite, Image> IMAGES = new EnumMap<>(Sprite.class);
    private static final Map<Explosion, List<Image>> EXPLOSION_FRAMES = new EnumMap<>(Explosion.class);
    private static final Map<BossArt, List<Image>> BOSS_FRAMES = new EnumMap<>(BossArt.class);

    /** Images the last {@link #preload} started, still decoding on a JavaFX loader thread. */
    private static final List<Image> WARMING = new ArrayList<>();

    /**
     * Display faces for menu headings, most wanted first.
     *
     * These are referenced from whatever the host already has rather than bundled: a font file is an
     * asset like any other, and most freely downloadable faces are not licensed for redistribution.
     */
    private static final List<String> DISPLAY_FONTS =
            List.of("Impact", "Haettenschweiler", "Arial Black", "Franklin Gothic Heavy",
                    "DejaVu Sans Condensed", "Verdana");

    private static boolean loaded;
    private static String displayFontFamily = "Serif";

    private Assets() {
    }

    /** Must run on the JavaFX thread before any scene is built. Idempotent. */
    public static void load() {
        if (loaded) {
            return;
        }
        for (Sprite sprite : Sprite.values()) {
            if (sprite.decodedOnDemand()) {
                continue;
            }
            Image image = decode(sprite.resourcePath(), sprite.width(), sprite.height());
            IMAGES.put(sprite, image);
        }
        for (Explosion explosion : Explosion.values()) {
            // Zero size means the explosion frames keep their natural dimensions; the size an
            // explosion draws at varies per hit, so it is applied at draw time instead.
            List<Image> frames = decodeFrames(explosion.frameCount(), explosion::framePath, 0, 0);
            EXPLOSION_FRAMES.put(explosion, frames);
        }
        displayFontFamily = loadDisplayFont();
        loaded = true;
    }

    public static Image image(Sprite sprite) {
        requireLoaded(sprite);
        Image image = IMAGES.computeIfAbsent(sprite,
                missing -> decode(missing.resourcePath(), missing.width(), missing.height()));
        return image;
    }

    public static List<Image> explosionFrames(Explosion explosion) {
        List<Image> frames = EXPLOSION_FRAMES.get(explosion);
        if (frames == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + explosion);
        }
        return frames;
    }

    public static List<Image> bossFrames(BossArt art) {
        requireLoaded(art);
        List<Image> frames = BOSS_FRAMES.computeIfAbsent(art, missing ->
                decodeFrames(missing.frameCount(), missing::framePath,
                        missing.width(), missing.height()));
        return frames;
    }

    /**
     * Starts decoding a level's art without blocking the caller.
     *
     * JavaFX decodes background-loaded images on its own loader thread, so the game can keep drawing
     * while this runs and poll {@link #warmedUp}. Takes the pieces rather than a level because the
     * asset package cannot depend on {@code mode}, which depends on it.
     */
    public static void preload(List<Sprite> layers, BossArt... arts) {
        WARMING.clear();
        for (Sprite layer : layers) {
            Image image = IMAGES.computeIfAbsent(layer, Assets::decodeInBackground);
            WARMING.add(image);
        }
        // Varargs because a multi-part flagship has more than one set of frames, and its heads
        // are on screen from the same tick the torso is.
        for (BossArt art : arts) {
            if (art == null) {
                continue;
            }
            requireLoaded(art);
            List<Image> frames = BOSS_FRAMES.computeIfAbsent(art, missing -> {
                List<Image> decoding = new ArrayList<>(missing.frameCount());
                for (int index = 1; index <= missing.frameCount(); index++) {
                    Image frame = decodeInBackground(missing.framePath(index),
                            missing.width(), missing.height());
                    decoding.add(frame);
                }
                return Collections.unmodifiableList(decoding);
            });
            WARMING.addAll(frames);
        }
    }

    /**
     * Whether every image the last {@link #preload} started has finished.
     *
     * A failed decode counts as finished: its progress never reaches 1, and waiting on it would hang
     * the caller rather than surface the problem.
     */
    public static boolean warmedUp() {
        for (Image image : WARMING) {
            if (image.getProgress() < 1 && image.getException() == null) {
                return false;
            }
        }
        return true;
    }

    private static void requireLoaded(Object requested) {
        if (!loaded) {
            throw new IllegalStateException("Assets.load() must run before requesting " + requested);
        }
    }

    /** Family name of the bundled display font, or a serif fallback if it could not be registered. */
    public static String displayFontFamily() {
        return displayFontFamily;
    }

    /** Decodes a numbered frame sequence, shared by explosions and boss animations. */
    private static List<Image> decodeFrames(int frameCount, IntFunction<String> framePath,
                                            double width, double height) {
        List<Image> frames = new ArrayList<>(frameCount);
        for (int index = 1; index <= frameCount; index++) {
            String path = framePath.apply(index);
            Image frame = decode(path, width, height);
            frames.add(frame);
        }
        List<Image> immutable = Collections.unmodifiableList(frames);
        return immutable;
    }

    /** A zero width or height means "decode at the image's natural size". */
    private static Image decode(String resourcePath, double width, double height) {
        InputStream stream = Assets.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled resource: " + resourcePath);
        }
        Image image = new Image(stream, width, height, true, true);
        return image;
    }

    private static Image decodeInBackground(Sprite sprite) {
        Image image = decodeInBackground(sprite.resourcePath(), sprite.width(), sprite.height());
        return image;
    }

    /**
     * Hands a decode off to JavaFX's loader thread.
     *
     * Addressed by URL rather than by stream: the background-loading constructor opens the source
     * itself, on its own thread, and cannot do that from a stream this method has already opened.
     */
    private static Image decodeInBackground(String resourcePath, double width, double height) {
        URL url = Assets.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Missing bundled resource: " + resourcePath);
        }
        Image image = new Image(url.toExternalForm(), width, height, true, true, true);
        return image;
    }

    /** First preferred display face the host actually has, else a generic serif. */
    private static String loadDisplayFont() {
        List<String> installed = Font.getFamilies();
        for (String candidate : DISPLAY_FONTS) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return "Serif";
    }
}
