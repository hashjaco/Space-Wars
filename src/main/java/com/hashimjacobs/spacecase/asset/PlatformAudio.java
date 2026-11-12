package com.hashimjacobs.spacecase.asset;

import java.net.URL;

import com.hashimjacobs.spacecase.OnAndroid;

/**
 * Playback for a platform where {@link SoundBank}'s own two backends cannot run.
 *
 * Java Sound and JavaFX media both need a JDK home and native libraries that the Android build
 * does not have, so there {@link SoundBank} hands the actual playback to this instead and keeps
 * everything else -- the audio thread, the cue choosing, pause and mute. Every call arrives on the
 * audio thread, one at a time.
 */
public interface PlatformAudio {


    /** Decodes an effect ahead of its first play. A failure leaves that effect silent. */
    void load(SoundFx effect, URL source);

    void play(SoundFx effect, double volume);

    void stopEffects();

    /** Starts a track from its beginning, looping, in place of whatever was playing. */
    void playMusic(MusicTrack track, URL source, double volume);

    void pauseMusic();

    void resumeMusic();

    void stopMusic();

    void setMusicVolume(double volume);

    /**
     * The implementation for this platform, or null where SoundBank's own backends apply.
     *
     * Found by name because the Android one is compiled only into the Android build, against a
     * library the desktop does not ship.
     */
    static PlatformAudio forThisPlatform() {
        if (!OnAndroid.ACTIVE) {
            return null;
        }
        return Holder.ANDROID;
    }

    /**
     * Starts the Android audio service now. Called first thing in {@code Launcher.main}.
     *
     * Creating any Gluon Attach service is what initialises Attach's native side. Until that has
     * happened, an Android lifecycle event -- Home pressed, another activity over this one -- is
     * handed to native code that dereferences a pointer nobody has set, and the app dies. Measured
     * on a Shield: Home four seconds after launch, with the service not yet created, crashed it.
     * So the service is created before JavaFX starts, not when the first sound is wanted.
     */
    static void startEarly() {
        forThisPlatform();
    }

    /** Created once, on first use. */
    final class Holder {
        private static final PlatformAudio ANDROID = create();

        private Holder() {
        }
    }

    private static PlatformAudio create() {
        try {
            return (PlatformAudio) Class.forName("com.hashimjacobs.spacecase.asset.AttachAudio")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Silent rather than null: null means "use the desktop backends", which on Android
            // throw on every sound.
            System.err.println("[audio] no Android audio, the game will be silent: " + unavailable);
            return SILENT;
        }
    }

    /** Plays nothing. */
    PlatformAudio SILENT = new PlatformAudio() {
        @Override public void load(SoundFx effect, URL source) { }
        @Override public void play(SoundFx effect, double volume) { }
        @Override public void stopEffects() { }
        @Override public void playMusic(MusicTrack track, URL source, double volume) { }
        @Override public void pauseMusic() { }
        @Override public void resumeMusic() { }
        @Override public void stopMusic() { }
        @Override public void setMusicVolume(double volume) { }
    };
}
