package com.hashimjacobs.spacecase.asset;

import java.util.EnumMap;
import java.util.Map;

/**
 * A creature flagship's own round, animated: the crab's spike, the worm's radioactive stardust.
 *
 * Each one rides on a {@link Sprite} -- the bullet's footprint, and frame one as its still -- so a
 * {@code Bullet} carrying it needs no new field, collision or sweep path. The renderer asks
 * {@link #of} and, when it gets a round back, plays these frames in place of the still. Which
 * boss fires which is {@code entity.Ordnance.artFor}.
 *
 * Frames are Sprixen AI VFX sliced by {@code tools/sprixen/import.py vfx round-<name> 64}.
 */
public enum Round {

    SPIKE(Sprite.ROUND_SPIKE, "fx-round-spike", 4, "#ffb070"),
    WEB(Sprite.ROUND_WEB, "fx-round-web", 4, "#dfe8ff"),
    STINGER(Sprite.ROUND_STINGER, "fx-round-stinger", 2, "#ffd23f"),
    STARDUST(Sprite.ROUND_STARDUST, "fx-round-stardust", 4, "#8dff3a"),
    EGG(Sprite.ROUND_EGG, "fx-round-egg", 4, "#c8e070"),
    FANG(Sprite.ROUND_FANG, "fx-round-fang", 4, "#ff8aa0");

    /** At sixty ticks a second, twelve frames a second: quick enough to read as spin at bullet size. */
    public static final int TICKS_PER_FRAME = 5;

    private static final Map<Sprite, Round> BY_SPRITE = new EnumMap<>(Sprite.class);

    static {
        for (Round round : values()) {
            BY_SPRITE.put(round.sprite, round);
        }
    }

    private final Sprite sprite;
    private final String directory;
    private final int frameCount;
    private final String haloHex;

    Round(Sprite sprite, String directory, int frameCount, String haloHex) {
        this.sprite = sprite;
        this.directory = directory;
        this.frameCount = frameCount;
        this.haloHex = haloHex;
    }

    /** The animated round a sprite stands for, or null for an ordinary still projectile. */
    public static Round of(Sprite sprite) {
        return BY_SPRITE.get(sprite);
    }

    public Sprite sprite() {
        return sprite;
    }

    public String framePath(int oneBasedIndex) {
        return "/sprites/" + directory + "/" + oneBasedIndex + ".png";
    }

    public int frameCount() {
        return frameCount;
    }

    /** Kept as hex so this stays loadable without the toolkit; the renderer builds the paint. */
    public String haloHex() {
        return haloHex;
    }
}
