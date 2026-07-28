package com.hashimjacobs.spacecase.asset;

import java.net.URL;
import java.util.Random;
import java.util.EnumMap;
import java.util.Map;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * All audio playback.
 *
 * Sound effects use AudioClip, which starts a fresh voice per call, so rapid fire overlaps instead
 * of each shot cutting off the previous one. Music uses MediaPlayer because it needs pause/resume.
 */
public final class SoundBank implements SoundPlayer {

    private final Map<SoundFx, AudioClip> clips = new EnumMap<>(SoundFx.class);
    private final Map<MusicTrack, MediaPlayer> music = new EnumMap<>(MusicTrack.class);
    private final Settings settings;
    private final Random random = new Random();
    private MusicCue currentCue;
    private MusicTrack currentTrack;

    public SoundBank(Settings settings) {
        this.settings = settings;
        for (SoundFx effect : SoundFx.values()) {
            AudioClip clip = new AudioClip(resolve(effect.resourcePath()));
            clips.put(effect, clip);
        }
        // Music players are created on first use rather than up front: the tracks are several
        // minutes each, and a run typically touches two of them.
        applyVolumes();
    }

    private MediaPlayer playerFor(MusicTrack track) {
        MediaPlayer existing = music.get(track);
        if (existing != null) {
            return existing;
        }
        Media media = new Media(resolve(track.resourcePath()));
        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setVolume(settings.musicVolume());
        music.put(track, player);
        return player;
    }

    @Override
    public void play(SoundFx effect) {
        AudioClip clip = clips.get(effect);
        double volume = effect.baseVolume() * settings.sfxVolume();
        clip.play(volume);
    }

    /**
     * Starts the music for a situation, choosing among the cue's tracks. Re-requesting the cue that
     * is already playing leaves it alone rather than restarting it, so returning to the menu from a
     * submenu does not jump the track back to the beginning.
     */
    public void playMusic(MusicCue cue) {
        if (currentCue == cue && currentTrack != null) {
            MediaPlayer already = playerFor(currentTrack);
            already.play();
            return;
        }
        stopMusic();
        currentCue = cue;
        currentTrack = cue.pick(random);
        MediaPlayer player = playerFor(currentTrack);
        player.seek(player.getStartTime());
        player.play();
    }

    /** The track currently selected, or null when nothing is playing. */
    public MusicTrack currentMusic() {
        return currentTrack;
    }

    public void pauseMusic() {
        if (currentTrack == null) {
            return;
        }
        MediaPlayer player = music.get(currentTrack);
        player.pause();
    }

    public void resumeMusic() {
        if (currentTrack == null) {
            return;
        }
        MediaPlayer player = music.get(currentTrack);
        player.play();
    }

    public void stopMusic() {
        if (currentTrack == null) {
            currentCue = null;
            return;
        }
        MediaPlayer player = music.get(currentTrack);
        player.stop();
        currentCue = null;
        currentTrack = null;
    }

    /** Call after the user changes a volume so the change is audible immediately. */
    public void applyVolumes() {
        for (MediaPlayer player : music.values()) {
            player.setVolume(settings.musicVolume());
        }
    }

    private static String resolve(String resourcePath) {
        URL url = SoundBank.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Missing bundled audio: " + resourcePath);
        }
        String external = url.toExternalForm();
        return external;
    }
}
