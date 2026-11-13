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
    UNDERCITY_BRAWL("undercity-brawl.mp3"),
    STARFALL_OVERTURE("starfall-overture.mp3"),
    DOGFIGHT_PROTOCOL("dogfight-protocol.mp3"),
    EVENT_HORIZON("event-horizon.mp3"),
    AEONS_END("aeons-end.mp3"),
    MORNING_AFTER_THE_DARK("morning-after-the-dark.mp3"),
    KESTREL_STATION("kestrel-station.mp3"),
    THE_NEST("the-nest.mp3"),
    THE_LEDGER("the-ledger.mp3"),
    THE_WALL("the-wall.mp3"),
    THE_STORM_BREAKS("the-storm-breaks.mp3"),
    // Dark horror, for the flagships that are creatures rather than ships (MusicCue.HORROR_BOSS).
    KILLER_FUCKING_SPACE_DEATH_WORM("killer-fucking-space-death-worm.mp3"),
    THIS_WORM_FUCKS("this-worm-fucks.mp3");
    // The two heavy tracks play for every flagship (MusicCue.BOSS); see ASSETS.md for origins.


    private final String fileName;

    MusicTrack(String fileName) {
        this.fileName = fileName;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }
}
