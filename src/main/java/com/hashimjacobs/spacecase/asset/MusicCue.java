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
    GAMEPLAY(MusicTrack.PIXEL_WOMP_RUN, MusicTrack.BASSLINE_RIOT),

    /** Battle mode. */
    BATTLE(MusicTrack.GRIME_QUEST, MusicTrack.BASSLINE_RIOT_VARIANT),

    /** Swapped in while a boss is on screen, and back out when it dies. */
    BOSS(MusicTrack.GRIME_QUEST_REMIX);

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
