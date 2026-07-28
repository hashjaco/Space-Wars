package com.hashimjacobs.spacecase.mode;

import com.hashimjacobs.spacecase.asset.MusicTrack;

/** The three ways to play. Mode differences are carried by {@link ModeRules}, not by branching. */
public enum GameMode {

    SOLO("Single Player", MusicTrack.MAIN,
            new ModeRules(1, true, true, true, false, false)),

    COOP("Co-op", MusicTrack.MAIN,
            new ModeRules(2, true, true, true, false, false)),

    BATTLE("Battle", MusicTrack.BATTLE,
            new ModeRules(2, false, true, true, true, true));

    private final String label;
    private final MusicTrack music;
    private final ModeRules rules;

    GameMode(String label, MusicTrack music, ModeRules rules) {
        this.label = label;
        this.music = music;
        this.rules = rules;
    }

    public String label() {
        return label;
    }

    public MusicTrack music() {
        return music;
    }

    public ModeRules rules() {
        return rules;
    }
}
