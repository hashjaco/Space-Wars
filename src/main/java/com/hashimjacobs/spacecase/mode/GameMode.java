package com.hashimjacobs.spacecase.mode;

import com.hashimjacobs.spacecase.asset.MusicCue;

/** The three ways to play. Mode differences are carried by {@link ModeRules}, not by branching. */
public enum GameMode {

    SOLO("Single Player", null,
            new ModeRules(1, true, true, true, false, false)),

    COOP("Co-op", null,
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

    /**
     * What plays while this mode is fought on a level: Battle has its own cue, and the campaign
     * modes take the level's galaxy's.
     */
    public MusicCue musicFor(Level level) {
        return music != null ? music : MusicCue.forGalaxy(level.galaxy());
    }

    public ModeRules rules() {
        return rules;
    }
}
