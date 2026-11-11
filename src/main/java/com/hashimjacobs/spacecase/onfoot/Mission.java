package com.hashimjacobs.spacecase.onfoot;

import java.util.List;

import com.hashimjacobs.spacecase.mode.Level;

/**
 * One on-foot stage: which level's clear sends the pilots down, its arenas, its boss, its look.
 *
 * One per galaxy, each after a level whose name already puts the fight on the ground. Arenas get
 * heavier per galaxy; the bosses share the Foreman's melee and each adds one signature to learn.
 */
public enum Mission {
    UNDERCITY(Level.UNDERCITY, "THE UNDERCITY", "RECOVER THE RIFT KEY", "THE RIFT KEY",
            Fighter.Kind.FOREMAN, "THE FOUNDRY FOREMAN", 0,
            List.of(new OnFootStage.Segment(560, 1560, 4, 0, 0, false),
                    new OnFootStage.Segment(1500, 2500, 4, 1, 0, false),
                    new OnFootStage.Segment(2440, 3440, 5, 2, 1, false),
                    new OnFootStage.Segment(3200, 4200, 0, 0, 0, true))),
    FORGEWORKS(Level.THE_FORGEWORKS, "THE FORGEWORKS", "SEIZE THE CRUCIBLE SEAL", "THE CRUCIBLE SEAL",
            Fighter.Kind.WARDEN, "THE CRUCIBLE WARDEN", -0.08,
            List.of(new OnFootStage.Segment(560, 1560, 4, 1, 0, false),
                    new OnFootStage.Segment(1500, 2500, 5, 1, 1, false),
                    new OnFootStage.Segment(2440, 3440, 5, 2, 1, false),
                    new OnFootStage.Segment(3200, 4200, 0, 0, 0, true))),
    UNDER_ICE(Level.UNDER_ICE, "UNDER THE ICE", "RECOVER THE COLD LENS", "THE COLD LENS",
            Fighter.Kind.MATRIARCH, "THE RIME MATRIARCH", 0.55,
            List.of(new OnFootStage.Segment(560, 1560, 5, 1, 0, false),
                    new OnFootStage.Segment(1500, 2500, 5, 2, 1, false),
                    new OnFootStage.Segment(2440, 3440, 6, 2, 1, false),
                    new OnFootStage.Segment(3200, 4200, 0, 0, 0, true))),
    MAG_STORM(Level.MAG_STORM_CAVERNS, "THE MAG-STORM CAVERNS", "TAKE THE STORM COIL", "THE STORM COIL",
            Fighter.Kind.TYRANT, "THE ARC TYRANT", 0.3,
            List.of(new OnFootStage.Segment(560, 1560, 5, 2, 0, false),
                    new OnFootStage.Segment(1500, 2500, 6, 2, 1, false),
                    new OnFootStage.Segment(2440, 3440, 6, 2, 2, false),
                    new OnFootStage.Segment(3200, 4200, 0, 0, 0, true))),
    HULK_DRIFT(Level.HULK_DRIFT, "ABOARD THE HULK", "CUT OUT THE NULL HEART", "THE NULL HEART",
            Fighter.Kind.REVENANT, "THE NULL REVENANT", 0.8,
            List.of(new OnFootStage.Segment(560, 1560, 6, 2, 1, false),
                    new OnFootStage.Segment(1500, 2500, 6, 2, 2, false),
                    new OnFootStage.Segment(2440, 3440, 7, 3, 2, false),
                    new OnFootStage.Segment(3200, 4200, 0, 0, 0, true)));

    final Level after;
    final String title, objective, prize, bossName;
    final Fighter.Kind boss;
    /** The crowd's and the key's hue shift, -1..1, so a reused cast still reads as local. */
    final double hue;
    final List<OnFootStage.Segment> segments;

    Mission(Level after, String title, String objective, String prize, Fighter.Kind boss,
            String bossName, double hue, List<OnFootStage.Segment> segments) {
        this.after = after;
        this.title = title;
        this.objective = objective;
        this.prize = prize;
        this.boss = boss;
        this.bossName = bossName;
        this.hue = hue;
        this.segments = segments;
    }

    /** The stage this level's clear leads to, or null for the forty-five that stay in the air. */
    public static Mission after(Level level) {
        for (Mission mission : values()) {
            if (mission.after == level) {
                return mission;
            }
        }
        return null;
    }

    public Level level() {
        return after;
    }

    public String title() {
        return title;
    }

    public String objective() {
        return objective;
    }

    public String prize() {
        return prize;
    }

    public String bossName() {
        return bossName;
    }

    public double hue() {
        return hue;
    }

    /** Where its own backdrop and floor live, under {@code /sprites/onfoot/}. */
    public String artFolder() {
        return name().toLowerCase().replace('_', '-');
    }
}
