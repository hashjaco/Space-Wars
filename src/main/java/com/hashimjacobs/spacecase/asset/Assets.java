package com.hashimjacobs.spacecase.asset;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Font;

import com.hashimjacobs.spacecase.GameConfig;

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
    /** One list per state an art has; states it lacks are never asked for. */
    private static final Map<BossArt, Map<BossArt.State, List<Image>>> BOSS_FRAMES = new EnumMap<>(BossArt.class);
    private static final Map<Round, List<Image>> ROUND_FRAMES = new EnumMap<>(Round.class);
    private static final Map<EnemyArt, List<Image>> ENEMY_FRAMES = new EnumMap<>(EnemyArt.class);
    private static final Map<MapArt, List<Image>> PLANET_FRAMES = new EnumMap<>(MapArt.class);
    private static final Map<MapArt, Image> SKIES = new EnumMap<>(MapArt.class);
    private static final Map<PoseKey, List<Image>> CHASSIS_FRAMES = new HashMap<>();

    private record PoseKey(ChassisArt art, String kit, ChassisArt.View view, float hue, float saturation,
                           int pose, boolean hit) {
    }

    private record BaseKey(ChassisArt art, String kit, ChassisArt.View view) {
    }

    /** Each CG hull's straight green loop as ARGB, decoded once; every paint and pose derives from it. */
    private static final Map<BaseKey, List<int[]>> CHASSIS_BASE = new HashMap<>();
    /** Twice the drawn box, so a hull stays sharp on a 2x display. */
    private static final int HULL_W = (int) (Sprite.P1_STRAIGHT.width() * 2), HULL_H = (int) (Sprite.P1_STRAIGHT.height() * 2);
    /** The turntable frames' own size: the garage shows the hull far bigger than it flies. */
    private static final int TURN_PX = 256;

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
            IMAGES.put(sprite, decode(sprite));
        }
        for (Explosion explosion : Explosion.values()) {
            // Zero size means the explosion frames keep their natural dimensions; the size an
            // explosion draws at varies per hit, so it is applied at draw time instead.
            List<Image> frames = decodeFrames(explosion.frameCount(), explosion::framePath, 0, 0);
            EXPLOSION_FRAMES.put(explosion, frames);
        }
        for (Round round : Round.values()) {
            Sprite still = round.sprite();
            ROUND_FRAMES.put(round, decodeFrames(round.frameCount(), round::framePath, still.width(), still.height()));
        }
        for (Sprite rock : ROCKS) {
            TUMBLES.put(rock, decodeFrames(TUMBLE_FRAMES,
                    i -> rock.resourcePath().replace("/1.png", "/" + i + ".png"), rock.width() * 2, rock.height() * 2));
        }
        displayFontFamily = loadDisplayFont();
        loaded = true;
    }

    /** Frames in an asteroid's tumble loop, one full turn, rendered by {@code tools/blender/asteroids.py}. */
    public static final int TUMBLE_FRAMES = 32;
    private static final List<Sprite> ROCKS = List.of(Sprite.ASTEROID_SMALL, Sprite.ASTEROID_BIG, Sprite.ASTEROID_HUGE);
    private static final Map<Sprite, List<Image>> TUMBLES = new EnumMap<>(Sprite.class);

    /** An asteroid's 3D tumble, decoded up front at twice its box: rocks arrive mid-fight, so no hitch on the first. */
    public static List<Image> tumbleFrames(Sprite rock) {
        List<Image> frames = TUMBLES.get(rock);
        if (frames == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + rock);
        }
        return frames;
    }

    public static Image image(Sprite sprite) {
        requireLoaded(sprite);
        Image image = IMAGES.computeIfAbsent(sprite, Assets::decode);
        return image;
    }

    public static List<Image> explosionFrames(Explosion explosion) {
        List<Image> frames = EXPLOSION_FRAMES.get(explosion);
        if (frames == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + explosion);
        }
        return frames;
    }

    public static List<Image> roundFrames(Round round) {
        List<Image> frames = ROUND_FRAMES.get(round);
        if (frames == null) {
            throw new IllegalStateException("Assets.load() must run before requesting " + round);
        }
        return frames;
    }

    public static List<Image> bossFrames(BossArt art) {
        return bossFrames(art, BossArt.State.IDLE);
    }

    /** The frames of one of an art's loops. Ask only for a state the art {@link BossArt#has}. */
    public static List<Image> bossFrames(BossArt art, BossArt.State state) {
        requireLoaded(art);
        Map<BossArt.State, List<Image>> states = BOSS_FRAMES.computeIfAbsent(art,
                missing -> new EnumMap<>(BossArt.State.class));
        List<Image> frames = states.computeIfAbsent(state, missing ->
                decodeFrames(art.frameCount(missing), index -> art.framePath(missing, index),
                        art.width(), art.height()));
        return frames;
    }

    /** A regular hostile's loop at its natural size; the box scales per wave, so it is applied at draw time. */
    /** A galaxy's turning planet for the universe map, decoded the first time the map shows it. */
    public static List<Image> planetFrames(MapArt art) {
        requireLoaded(art);
        return PLANET_FRAMES.computeIfAbsent(art,
                missing -> decodeFrames(MapArt.FRAMES, missing::planetFramePath, 0, 0));
    }

    /** The sky behind a galaxy's route. */
    public static Image mapSky(MapArt art) {
        requireLoaded(art);
        return SKIES.computeIfAbsent(art, missing -> decode(missing.skyPath(), 0, 0));
    }

    public static List<Image> enemyFrames(EnemyArt art) {
        requireLoaded(art);
        List<Image> frames = ENEMY_FRAMES.computeIfAbsent(art,
                missing -> decodeFrames(missing.frameCount(), missing::framePath, 0, 0));
        return frames;
    }

    /**
     * A player hull's loop in one view, kit, paint and pose, derived from its green loop by
     * {@link HullPaint} on first request. Top and side are twice the stock hull's box (transposed
     * for the profile); the renderer draws them into the ship's box. The turntable keeps its size.
     *
     * @param kit {@code Kit.slug()}, or null for the bare hull
     */
    public static List<Image> chassisFrames(ChassisArt art, String kit, ChassisArt.View view, float hue,
                                            float saturation, int pose, boolean hit) {
        requireLoaded(art);
        return CHASSIS_FRAMES.computeIfAbsent(new PoseKey(art, kit, view, hue, saturation, pose, hit), key -> {
            int w = view == ChassisArt.View.TURN ? TURN_PX : view == ChassisArt.View.SIDE ? HULL_H : HULL_W;
            int h = view == ChassisArt.View.TURN ? TURN_PX : view == ChassisArt.View.SIDE ? HULL_W : HULL_H;
            List<int[]> base = CHASSIS_BASE.computeIfAbsent(new BaseKey(art, kit, view), missing -> {
                List<int[]> decoded = new ArrayList<>(view.frameCount());
                for (int index = 1; index <= view.frameCount(); index++) {
                    Image image = decode(art.framePath(kit, view, index), w, h);
                    int[] argb = new int[w * h];
                    image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
                    decoded.add(argb);
                }
                return decoded;
            });
            List<Image> frames = new ArrayList<>(base.size());
            for (int[] argb : base) {
                WritableImage frame = new WritableImage(w, h);
                frame.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(),
                        HullPaint.derive(argb, w, h, hue, saturation, pose, hit, view == ChassisArt.View.SIDE), 0, w);
                frames.add(frame);
            }
            return Collections.unmodifiableList(frames);
        });
    }

    /**
     * Starts decoding a level's art without blocking the caller.
     *
     * JavaFX decodes background-loaded images on its own loader thread, so the game can keep drawing
     * while this runs and poll {@link #warmedUp}. Takes the pieces rather than a level because the
     * asset package cannot depend on {@code mode}, which depends on it.
     */
    public static void preload(List<Sprite> layers, List<EnemyArt> hostiles, BossArt... arts) {
        WARMING.clear();
        // Only the level ahead stays decoded: a flagship's loops at 512 px run to tens of megabytes,
        // and fifty of them would not fit. Called from the debrief, when the last one is dead and
        // swept, so nothing on screen is dropped.
        Set<BossArt> keep = new HashSet<>();
        for (BossArt art : arts) {
            if (art != null) {
                keep.add(art);
            }
        }
        BOSS_FRAMES.keySet().retainAll(keep);
        ENEMY_FRAMES.keySet().retainAll(new HashSet<>(hostiles));
        for (Sprite layer : layers) {
            Image image = IMAGES.computeIfAbsent(layer, Assets::decodeInBackground);
            WARMING.add(image);
        }
        for (EnemyArt art : hostiles) {
            requireLoaded(art);
            List<Image> frames = ENEMY_FRAMES.computeIfAbsent(art, missing -> {
                List<Image> decoding = new ArrayList<>(missing.frameCount());
                for (int index = 1; index <= missing.frameCount(); index++) {
                    decoding.add(decodeInBackground(missing.framePath(index), 0, 0));
                }
                return Collections.unmodifiableList(decoding);
            });
            WARMING.addAll(frames);
        }
        // Varargs because a multi-part flagship has more than one set of frames, and its heads
        // are on screen from the same tick the torso is.
        for (BossArt art : keep) {
            requireLoaded(art);
            Map<BossArt.State, List<Image>> states = BOSS_FRAMES.computeIfAbsent(art,
                    missing -> new EnumMap<>(BossArt.State.class));
            for (BossArt.State state : BossArt.State.values()) {
                if (!art.has(state)) {
                    continue;
                }
                List<Image> frames = states.computeIfAbsent(state, missing -> {
                    List<Image> decoding = new ArrayList<>(art.frameCount(missing));
                    for (int index = 1; index <= art.frameCount(missing); index++) {
                        decoding.add(decodeInBackground(art.framePath(missing, index), art.width(), art.height()));
                    }
                    return Collections.unmodifiableList(decoding);
                });
                WARMING.addAll(frames);
            }
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

    /**
     * A sprite at twice its box, so the game canvas, which rasterizes at window resolution, has the
     * pixels to stay sharp up to a 2x window or a Retina screen; the sources are cut at 3x. Arena-
     * sized backdrops stay 1:1: their sources are 996x864, and doubling would only cost memory.
     */
    private static Image decode(Sprite sprite) {
        double k = sprite.width() >= GameConfig.WIDTH ? 1 : 2;
        return decode(sprite.resourcePath(), sprite.width() * k, sprite.height() * k);
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
