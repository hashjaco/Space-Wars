package com.hashimjacobs.spacecase.asset;

/**
 * The soundtrack. Composed by Hashim Jacobs; see ASSETS.md.
 *
 * Tracks use MediaPlayer rather than AudioClip so they can be paused, and are created on first play
 * rather than up front since each is around three minutes long.
 *
 * Which track plays when is decided by {@link MusicCue}, not here.
 */
public enum MusicTrack {

    ARCADE_WOMPS("arcade-womps.mp3"),
    PIXEL_WOMP_RUN("pixel-womp-run.mp3"),
    BASSLINE_RIOT("bassline-riot-remastered.mp3"),
    BASSLINE_RIOT_VARIANT("bassline-riot-remastered-variant.mp3"),
    GRIME_QUEST("grime-quest.mp3"),
    GRIME_QUEST_REMIX("grime-quest-remix.mp3");

    private final String fileName;

    MusicTrack(String fileName) {
        this.fileName = fileName;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }
}
