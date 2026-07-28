package com.hashimjacobs.spacecase.asset;

import java.net.URL;
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
    private MusicTrack current;

    public SoundBank(Settings settings) {
        this.settings = settings;
        for (SoundFx effect : SoundFx.values()) {
            AudioClip clip = new AudioClip(resolve(effect.resourcePath()));
            clips.put(effect, clip);
        }
        for (MusicTrack track : MusicTrack.values()) {
            Media media = new Media(resolve(track.resourcePath()));
            MediaPlayer player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            music.put(track, player);
        }
        applyVolumes();
    }

    @Override
    public void play(SoundFx effect) {
        AudioClip clip = clips.get(effect);
        double volume = effect.baseVolume() * settings.sfxVolume();
        clip.play(volume);
    }

    public void playMusic(MusicTrack track) {
        if (current == track) {
            MediaPlayer already = music.get(track);
            already.play();
            return;
        }
        stopMusic();
        current = track;
        MediaPlayer player = music.get(track);
        player.seek(player.getStartTime());
        player.play();
    }

    public void pauseMusic() {
        if (current == null) {
            return;
        }
        MediaPlayer player = music.get(current);
        player.pause();
    }

    public void resumeMusic() {
        if (current == null) {
            return;
        }
        MediaPlayer player = music.get(current);
        player.play();
    }

    public void stopMusic() {
        if (current == null) {
            return;
        }
        MediaPlayer player = music.get(current);
        player.stop();
        current = null;
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
