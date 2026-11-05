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
    GRIME_QUEST_REMIX("grime-quest-remix.mp3"),
    STARLIGHT_CIRCUIT("starlight-circuit.mp3"),
    GARAGE_MUSIC("garage-music.mp3"),
    DEATH_METAL("death-metal.mp3"),
    DEATH_PUNK("death-punk.mp3"),
    // One per galaxy and one for the last flagship, generated with Sprixen (see ASSETS.md).
    VERDANCE_SX("verdance-sx.mp3"),
    ASHFALL_SX("ashfall-sx.mp3"),
    CRYONIS_SX("cryonis-sx.mp3"),
    TEMPEST_SX("tempest-sx.mp3"),
    NULL_SX("null-sx.mp3"),
    AEON_SX("aeon-sx.mp3");

    private final String fileName;

    MusicTrack(String fileName) {
        this.fileName = fileName;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }
}
