package com.hashimjacobs.spacecase.asset;

/** Looping background tracks. These use MediaPlayer rather than AudioClip so they can be paused. */
public enum MusicTrack {

    MAIN("main-theme.mp3"),
    BATTLE("battle-theme.mp3");

    private final String fileName;

    MusicTrack(String fileName) {
        this.fileName = fileName;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }
}
