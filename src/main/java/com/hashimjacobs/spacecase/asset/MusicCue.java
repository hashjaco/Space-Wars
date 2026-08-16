package com.hashimjacobs.spacecase.asset;

import java.util.List;
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

    /** Single player and co-op. */
    GAMEPLAY(MusicTrack.PIXEL_WOMP_RUN, MusicTrack.BASSLINE_RIOT, MusicTrack.STARLIGHT_CIRCUIT),

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
    GARAGE(MusicTrack.GARAGE_MUSIC);

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
