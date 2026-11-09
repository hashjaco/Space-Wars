package com.hashimjacobs.spacecase.asset;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.hashimjacobs.spacecase.mode.Galaxy;

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
     * Single player and co-op, one cue per galaxy. Each rotates four of the six gameplay tracks,
     * its signature one first, and {@code SoundBank} never plays the same one twice running -- one
     * track per galaxy made ten levels in a row sound identical. Galaxies share tracks because
     * there are only six; what keeps a galaxy its own is which four, and its signature.
     *
     * The heavy tracks are not here at all: metal and hard rock are saved for the finales.
     * Resolved by {@link #forGalaxy(Galaxy)}; {@code GameMode.musicFor(Level)} is what callers use.
     */
    VERDANCE(MusicTrack.PIXEL_WOMP_RUN, MusicTrack.STARLIGHT_CIRCUIT, MusicTrack.GRIME_QUEST,
            MusicTrack.BASSLINE_RIOT),
    ASHFALL(MusicTrack.BASSLINE_RIOT, MusicTrack.GRIME_QUEST_REMIX, MusicTrack.PIXEL_WOMP_RUN,
            MusicTrack.BASSLINE_RIOT_VARIANT),
    CRYONIS(MusicTrack.STARLIGHT_CIRCUIT, MusicTrack.BASSLINE_RIOT_VARIANT, MusicTrack.GRIME_QUEST,
            MusicTrack.PIXEL_WOMP_RUN),
    TEMPEST(MusicTrack.GRIME_QUEST, MusicTrack.BASSLINE_RIOT, MusicTrack.GRIME_QUEST_REMIX,
            MusicTrack.STARLIGHT_CIRCUIT),
    NULL(MusicTrack.GRIME_QUEST_REMIX, MusicTrack.BASSLINE_RIOT_VARIANT, MusicTrack.STARLIGHT_CIRCUIT,
            MusicTrack.GRIME_QUEST),

    /** Battle mode. */
    BATTLE(MusicTrack.BASSLINE_RIOT_VARIANT),

    /**
     * Swapped in while an ordinary flagship is on screen, and back out when it dies. Several, so
     * the swap always changes the song rather than landing on whatever the level was playing.
     */
    BOSS(MusicTrack.GRIME_QUEST_REMIX, MusicTrack.BASSLINE_RIOT_VARIANT, MusicTrack.GRIME_QUEST),

    /** A galaxy's last flagship. The hard fights are the only ones that get metal and hard rock. */
    FINALE_BOSS(MusicTrack.DEATH_PUNK, MusicTrack.DEATH_METAL),


    /** The between-levels garage, between the debrief and the warp. */
    GARAGE(MusicTrack.GARAGE_MUSIC),

    /** Aeon, at the end of everything, with a theme to itself like the two monsters. */
    AEON_BOSS(MusicTrack.DEATH_METAL);

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

    /**
     * A track that is not in {@code avoid}, so a cue does not repeat its last pick or land on the
     * song already playing. Falls back to any track when the cue has nothing else.
     */
    public MusicTrack pick(Random random, Set<MusicTrack> avoid) {
        List<MusicTrack> fresh = new ArrayList<>(tracks);
        fresh.removeAll(avoid);
        return fresh.isEmpty() ? pick(random) : fresh.get(random.nextInt(fresh.size()));
    }

    public List<MusicTrack> tracks() {
        return tracks;
    }
}
