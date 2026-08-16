package com.hashimjacobs.spacecase.asset;

import java.net.URL;
import java.util.Random;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * All audio playback, on a thread of its own.
 *
 * Sound effects use AudioClip, which starts a fresh voice per call, so rapid fire overlaps instead
 * of each shot cutting off the previous one. Music uses MediaPlayer because it needs to be swapped
 * between situations.
 *
 * <h2>Why none of it runs on the JavaFX thread</h2>
 *
 * Every call here ends in the platform's native media stack, and that stack can die mid-session --
 * twice now, taking music and effects together. Once it has, the next native call made against it
 * does not fail, it never returns: the application thread was found RUNNABLE with no Java frames
 * at all, which is a thread inside native code. Because that thread also draws the game, a dead
 * audio engine froze the whole window and cost a force-quit.
 *
 * So audio owns one daemon thread and every caller hands work to it. A wedged audio engine now
 * costs silence rather than the game. Nothing reads playback state back, so nothing needs to wait,
 * and one thread keeps the ordering the callers do rely on -- a stop still lands before the play
 * that follows it.
 */
public final class SoundBank implements SoundPlayer {

    /**
     * Far above healthy depth, so a full queue means the thread is stuck rather than busy.
     *
     * Bounded rather than unbounded on purpose: if the audio thread wedges, requests have to be
     * dropped. An unbounded queue would grow one entry per shot fired for the rest of the session.
     */
    private static final int QUEUE_DEPTH = 64;

    private final ExecutorService audio = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_DEPTH),
            runnable -> {
                Thread thread = new Thread(runnable, "audio");
                // Daemon, so a wedged audio thread cannot keep the JVM alive after the window is
                // gone. Silence is recoverable by restarting; a process that will not exit is not.
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    /** Touched only on the audio thread, from here down. */
    private final Map<SoundFx, AudioClip> clips = new EnumMap<>(SoundFx.class);
    private final VoiceLimiter voices = new VoiceLimiter();
    /** A per-effect ceiling as well, so one repeater cannot take the whole global budget. */
    private final Map<SoundFx, VoiceLimiter> perEffectVoices = new EnumMap<>(SoundFx.class);
    /**
     * One player per track, kept for the life of the process.
     *
     * Built once and reused rather than opened per switch, because opening is the operation that
     * wedges: soaked against this game's own switch pattern, constructing a player per switch
     * blocked the JavaFX thread inside native code within a few minutes -- with or without a
     * matching {@code dispose()}, and even with every call made off that thread, since the media
     * framework services its own events there. The same soak with no music switching at all ran
     * clean. Reuse bounds construction at ten for a whole session instead of one per switch.
     */
    private final Map<MusicTrack, MediaPlayer> music = new EnumMap<>(MusicTrack.class);
    private MusicCue currentCue;
    private MusicTrack currentTrack;

    private final Settings settings;
    private final Random random = new Random();

    public SoundBank(Settings settings) {
        this.settings = settings;
        // Queued rather than decoded here: nine clips, one of them nine seconds long, used to be
        // decoded synchronously during startup. Everything below queues behind this task, so the
        // clips are loaded before any play can reach them.
        audio.execute(this::loadClips);
    }

    private void loadClips() {
        for (SoundFx effect : SoundFx.values()) {
            AudioClip clip = new AudioClip(resolve(effect.resourcePath()));
            clips.put(effect, clip);
            perEffectVoices.put(effect, new VoiceLimiter(VoiceLimiter.MAX_VOICES_PER_EFFECT));
        }
    }

    @Override
    public void play(SoundFx effect) {
        // Volume is resolved here rather than on the audio thread: it is a plain field read, and
        // passing the value across means the setting is never read from two threads at once.
        double volume = effect.baseVolume() * settings.sfxVolume();
        audio.execute(() -> {
            AudioClip clip = clips.get(effect);
            if (clip == null) {
                return;
            }
            // Dropped rather than played once too many voices are already sounding. Each one holds
            // a native media player open, and enough of them at once hangs the window.
            long now = System.nanoTime();
            long duration = (long) (effect.seconds() * 1_000_000_000L);
            // Global ceiling checked before the per-effect slot is taken, so a sound that is about
            // to be dropped does not spend its own effect's budget on the way out.
            if (!voices.hasFree(now)) {
                return;
            }
            if (!perEffectVoices.get(effect).claim(now, duration)) {
                return;
            }
            voices.claim(now, duration);
            clip.play(volume);
        });
    }

    /**
     * Starts the music for a situation, choosing among the cue's tracks. Re-requesting the cue that
     * is already playing leaves it alone rather than restarting it, so returning to the menu from a
     * submenu does not jump the track back to the beginning.
     */
    public void playMusic(MusicCue cue) {
        // Volume read here for the same reason as in play(): the setting is written on the JavaFX
        // thread, so carrying the value across leaves the audio thread reading nothing shared.
        double volume = settings.musicVolume();
        audio.execute(() -> playMusicNow(cue, volume));
    }

    private void playMusicNow(MusicCue cue, double volume) {
        if (currentCue == cue && currentTrack != null) {
            playerFor(currentTrack, volume).play();
            return;
        }
        stopMusicNow();
        currentCue = cue;
        currentTrack = cue.pick(random);
        MediaPlayer player = playerFor(currentTrack, volume);
        // Reused players carry their old position, so a cue that comes back around starts over
        // rather than resuming mid-phrase.
        player.seek(player.getStartTime());
        player.setVolume(volume);
        player.play();
    }

    /** Stops the music. The player stays built, ready to be started again. */
    public void stopMusic() {
        audio.execute(this::stopMusicNow);
    }

    private void stopMusicNow() {
        currentCue = null;
        if (currentTrack == null) {
            return;
        }
        // Stopped, never disposed. Disposing would mean rebuilding on the way back, and building
        // is the thing that hangs -- see the note on the music map.
        music.get(currentTrack).stop();
        currentTrack = null;
    }

    private MediaPlayer playerFor(MusicTrack track, double volume) {
        MediaPlayer existing = music.get(track);
        if (existing != null) {
            return existing;
        }
        Media media = new Media(resolve(track.resourcePath()));
        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setVolume(volume);
        reportFailures(player, track);
        music.put(track, player);
        return player;
    }

    /**
     * Says why the audio stack died, the next time it does.
     *
     * There was no error handling here at all, so an engine that failed mid-level failed silently
     * and left nothing to read afterwards but the report that the sound had gone. These two lines
     * are the only thing that will name that fault.
     */
    private static void reportFailures(MediaPlayer player, MusicTrack track) {
        player.setOnError(() ->
                System.err.println("[audio] " + track + " failed: " + player.getError()));
        player.statusProperty().addListener((property, was, now) -> {
            if (now == MediaPlayer.Status.HALTED) {
                System.err.println("[audio] " + track + " halted: " + player.getError());
            }
        });
    }

    /** Call after the user changes a volume so the change is audible immediately. */
    public void applyVolumes() {
        double volume = settings.musicVolume();
        audio.execute(() -> {
            for (MediaPlayer player : music.values()) {
                player.setVolume(volume);
            }
        });
    }

    /** Releases the audio thread with the window. Safe to call more than once. */
    public void shutdown() {
        audio.shutdownNow();
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
