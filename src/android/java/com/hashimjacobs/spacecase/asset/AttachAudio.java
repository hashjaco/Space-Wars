package com.hashimjacobs.spacecase.asset;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import com.gluonhq.attach.audio.Audio;
import com.gluonhq.attach.audio.AudioService;

/**
 * Android playback through Gluon Attach: effects in an Android SoundPool, music in an Android
 * MediaPlayer. Compiled only by the {@code mobile} profile; see {@link PlatformAudio}.
 *
 * Attach copies each file out of the app by its file name before Android can open it, which is
 * safe here only because every file under {@code /sounds} has a name of its own.
 */
public final class AttachAudio implements PlatformAudio {

    private final AudioService service = AudioService.create()
            .orElseThrow(() -> new IllegalStateException("no Attach audio service"));
    private final Map<SoundFx, Audio> effects = new EnumMap<>(SoundFx.class);
    private final Map<MusicTrack, Audio> music = new EnumMap<>(MusicTrack.class);
    private Audio playing;

    @Override
    public void load(SoundFx effect, URL source) {
        service.loadSound(source).ifPresentOrElse(audio -> effects.put(effect, audio),
                () -> System.err.println("[audio] " + effect + " will be silent"));
    }

    @Override
    public void play(SoundFx effect, double volume) {
        Audio audio = effects.get(effect);
        if (audio != null) {
            audio.setVolume(volume);
            audio.play();
        }
    }

    @Override
    public void stopEffects() {
        for (Audio audio : effects.values()) {
            audio.stop();
        }
    }

    @Override
    public void playMusic(MusicTrack track, URL source, double volume) {
        stopMusic();
        Audio audio = music.get(track);
        if (audio == null) {
            audio = service.loadMusic(source).orElse(null);
            if (audio == null) {
                System.err.println("[audio] " + track + " will be silent");
                return;
            }
            audio.setLooping(true);
            music.put(track, audio);
        }
        audio.setVolume(volume);
        audio.play();
        playing = audio;
    }

    @Override
    public void pauseMusic() {
        if (playing != null) {
            playing.pause();
        }
    }

    @Override
    public void resumeMusic() {
        if (playing != null) {
            playing.play();
        }
    }

    /** Attach's stop on a track is a pause and a rewind, so the track is ready to start over. */
    @Override
    public void stopMusic() {
        if (playing != null) {
            playing.stop();
            playing = null;
        }
    }

    @Override
    public void setMusicVolume(double volume) {
        if (playing != null) {
            playing.setVolume(volume);
        }
    }
}
