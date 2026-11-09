package com.hashimjacobs.spacecase.asset;

/**
 * The universe map's art for one galaxy: a planet that turns, and the sky behind its route.
 *
 * One constant per {@code mode.Galaxy}, by the same name -- {@code asset} cannot see {@code mode},
 * so the scene code resolves it with {@code MapArt.valueOf(galaxy.name())}. Sprixen, imported by
 * {@code tools/sprixen/map.py}; see ASSETS.md.
 */
public enum MapArt {

    VERDANCE, ASHFALL, CRYONIS, TEMPEST, NULL;

    /** 24 frames spread over the ~9.5 s premium loop, at the pace the CG flagships play theirs. */
    public static final int FRAMES = 24;
    public static final int TICKS_PER_FRAME = 24;

    private String slug() {
        return name().toLowerCase();
    }

    /** One planet frame, numbered from 1 like every other frame set. */
    public String planetFramePath(int index) {
        return "/sprites/map-cg/planet-" + slug() + "/" + index + ".png";
    }

    /** 1240x620: the map canvas's height, and wider than it so the sky can drift. */
    public String skyPath() {
        return "/sprites/map-cg/sky-" + slug() + ".png";
    }

    public static int frameIndexAt(int tick) {
        return (tick / TICKS_PER_FRAME) % FRAMES;
    }
}
