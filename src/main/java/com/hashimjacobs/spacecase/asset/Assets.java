package com.hashimjacobs.spacecase.asset;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

/**
 * Eagerly decodes every image once, at startup.
 *
 * Everything resolves through the classloader, so the game no longer depends on being launched
 * from the project directory and can run from a packaged jar.
 */
public final class Assets {

    private static final Map<Sprite, Image> IMAGES = new EnumMap<>(Sprite.class);
    private static final Map<Explosion, List<Image>> EXPLOSION_FRAMES = new EnumMap<>(Explosion.class);

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
            Image image = decode(sprite.resourcePath(), sprite.width(), sprite.height());
            IMAGES.put(sprite, image);
        }
        for (Explosion explosion : Explosion.values()) {
            List<Image> frames = decodeFrames(explosion);
            EXPLOSION_FRAMES.put(explosion, frames);
        }
        displayFontFamily = loadDisplayFont();
        loaded = true;
    }

    public static Image image(Sprite sprite) {
        Image image = IMAGES.get(sprite);
        if (image == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + sprite);
        }
        return image;
    }

    public static List<Image> explosionFrames(Explosion explosion) {
        List<Image> frames = EXPLOSION_FRAMES.get(explosion);
        if (frames == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + explosion);
        }
        return frames;
    }

    /** Family name of the bundled display font, or a serif fallback if it could not be registered. */
    public static String displayFontFamily() {
        return displayFontFamily;
    }

    private static List<Image> decodeFrames(Explosion explosion) {
        List<Image> frames = new ArrayList<>(explosion.frameCount());
        for (int index = 1; index <= explosion.frameCount(); index++) {
            String path = explosion.framePath(index);
            Image frame = decode(path, 0, 0);
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
