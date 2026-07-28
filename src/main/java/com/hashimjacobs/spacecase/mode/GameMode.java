package com.hashimjacobs.spacecase.mode;

import com.hashimjacobs.spacecase.asset.MusicCue;

/** The three ways to play. Mode differences are carried by {@link ModeRules}, not by branching. */
public enum GameMode {

    SOLO("Single Player", MusicCue.GAMEPLAY,
            new ModeRules(1, true, true, true, false, false)),

    COOP("Co-op", MusicCue.GAMEPLAY,
            new ModeRules(2, true, true, true, false, false)),

    BATTLE("Battle", MusicCue.BATTLE,
            new ModeRules(2, false, true, true, true, true));

    private final String label;
    private final MusicCue music;
    private final ModeRules rules;

    GameMode(String label, MusicCue music, ModeRules rules) {
        this.label = label;
        this.music = music;
        this.rules = rules;
    }

    public String label() {
        return label;
    }

    public MusicCue music() {
        return music;
    }

    public ModeRules rules() {
        return rules;
    }
}
