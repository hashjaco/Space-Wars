package com.hashimjacobs.spacecase.asset;

import java.util.List;

import com.hashimjacobs.spacecase.mode.Galaxy;
import java.util.Random;

/**
 * When music plays, rather than which track.
 *
 * A cue holds every track that suits a situation and picks one per round, so replaying a mode does
 * not always sound identical. Re-pointing a cue at different tracks is the one place to edit if the
 * pairings feel wrong.
 */
public enum MusicCue {

    /** Start screen, the submenus, and the game over screen. */
    MENU(MusicTrack.ARCADE_WOMPS),

    /**
     * Single player and co-op, one cue per galaxy: each pairs a galaxy's own generated track with
     * one of the original gameplay tracks, so a galaxy sounds like itself without sounding like
     * only one thing. Tempest and Null are later and stranger, and get their own track alone.
     * Resolved by {@link #forGalaxy(Galaxy)}; {@code GameMode.musicFor(Level)} is what callers use.
     */
    VERDANCE(MusicTrack.VERDANCE_SX, MusicTrack.PIXEL_WOMP_RUN),
    ASHFALL(MusicTrack.ASHFALL_SX, MusicTrack.BASSLINE_RIOT),
    CRYONIS(MusicTrack.CRYONIS_SX, MusicTrack.STARLIGHT_CIRCUIT),
    TEMPEST(MusicTrack.TEMPEST_SX),
    NULL(MusicTrack.NULL_SX),

    /** Battle mode. */
    BATTLE(MusicTrack.GRIME_QUEST, MusicTrack.BASSLINE_RIOT_VARIANT),

    /** Swapped in while a boss is on screen, and back out when it dies. */
    BOSS(MusicTrack.GRIME_QUEST_REMIX),

    /**
     * The two monsters, each with a cue to itself.
     *
     * Their own cues rather than two more tracks on {@link #BOSS}, because a cue picks at random
     * and death metal over the Sentinel would be the wrong fight entirely.
     */
    HYDRA_BOSS(MusicTrack.DEATH_METAL),
    LEVIATHAN_BOSS(MusicTrack.DEATH_PUNK),

    /** The between-levels garage, between the debrief and the warp. */
    GARAGE(MusicTrack.GARAGE_MUSIC),

    /** Aeon, at the end of everything, with a theme to itself like the two monsters. */
    AEON_BOSS(MusicTrack.AEON_SX);

    /** The gameplay cue for a galaxy: the five constants above, in galaxy order. */
    public static MusicCue forGalaxy(Galaxy galaxy) {
        return switch (galaxy) {
            case VERDANCE -> VERDANCE;
            case ASHFALL -> ASHFALL;
            case CRYONIS -> CRYONIS;
            case TEMPEST -> TEMPEST;
            case NULL -> NULL;
        };
    }

    private final List<MusicTrack> tracks;

    MusicCue(MusicTrack... tracks) {
        this.tracks = List.of(tracks);
    }

    public MusicTrack pick(Random random) {
        MusicTrack chosen = tracks.get(random.nextInt(tracks.size()));
        return chosen;
    }

    public List<MusicTrack> tracks() {
        return tracks;
    }
}
