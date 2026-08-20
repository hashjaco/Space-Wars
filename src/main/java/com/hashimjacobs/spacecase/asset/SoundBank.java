package com.hashimjacobs.spacecase.asset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * All audio playback, on a thread of its own.
 *
 * Sound effects play through {@code javax.sound.sampled}; music plays through JavaFX MediaPlayer.
 * The split is the fix for a freeze, not a matter of taste.
 *
 * Effects used to be JavaFX AudioClips. Every {@code AudioClip.play()} builds a whole native media
 * player, with an event-queue thread of its own, and tears it down when the voice ends -- one per
 * shot fired, however short the sound. On macOS that runs on a different native backend from the
 * one the MP3 soundtrack uses, and the two contend for a shared lock. A dump taken during the third
 * freeze caught all three ends of it at once: the application thread inside the soundtrack's
 * presentation-time call, another thread disposing an effect voice, and a media timer blocked
 * between them. Because the application thread also draws the game, that took the window with it.
 *
 * Java Sound opens each voice once, at startup, and reuses it. There is no per-play construction to
 * collide with anything, and the GStreamer backend is never loaded at all. Music stays on
 * MediaPlayer because the soundtrack is MP3 and the JDK cannot decode it -- which is fine, since
 * one backend on its own was never the problem.
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

    /**
     * What the effects path is actually doing.
     *
     * There was no count of any kind here, and no error callback either -- {@code AudioClip} does
     * not offer one -- so a play that was refused, discarded or never sounded left nothing behind.
     * That absence is why the freeze took three episodes and a thread dump to pin down. Cheap to
     * keep, and it is what {@code AudioSoak} reads.
     */
    private final Counter requests = new Counter();
    private final Counter started = new Counter();
    private final Counter ceilingDrops = new Counter();
    private final Counter queueDrops = new Counter();

    private final ExecutorService audio = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_DEPTH),
            runnable -> {
                Thread thread = new Thread(runnable, "audio");
                // Daemon, so a wedged audio thread cannot keep the JVM alive after the window is
                // gone. Silence is recoverable by restarting; a process that will not exit is not.
                thread.setDaemon(true);
                return thread;
            },
            (dropped, executor) -> queueDrops.increment());

    /**
     * Voices per effect, opened once and reused.
     *
     * A {@link Clip} plays one copy of itself at a time, so overlapping shots need a clip each --
     * which makes the pool the voice ceiling, and an exact one: a clip is free when it has stopped
     * running, not when a table predicts it should have. That is the whole reason the duration
     * bookkeeping this replaced could be deleted rather than ported.
     *
     * Empty for every effect when the machine has no audio device, which is how this degrades.
     */
    private static final int VOICES_PER_EFFECT = 3;

    /**
     * Voices allowed at once across everything.
     *
     * Not a resource limit any more -- nothing is constructed per play -- but twenty-seven sounds
     * at once is a wash of noise rather than a fight. Counted from the clips actually running.
     */
    private static final int MAX_VOICES = 8;

    /** Touched only on the audio thread, from here down. */
    private final Map<SoundFx, Clip[]> clips = new EnumMap<>(SoundFx.class);
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

    /**
     * Opens every voice of every effect, once.
     *
     * A machine with no audio device -- a headless build box, a stripped container -- gets one line
     * on stderr and a silent game, rather than a stack trace out of a background thread nobody
     * reads. Same bargain the gamepad reader makes when SDL will not start.
     */
    private void loadClips() {
        for (SoundFx effect : SoundFx.values()) {
            try {
                clips.put(effect, openVoices(effect));
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException
                    | IllegalArgumentException unavailable) {
                System.err.println("[audio] " + effect + " will be silent: " + unavailable);
            }
        }
    }

    private static Clip[] openVoices(SoundFx effect)
            throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        URL source = SoundBank.class.getResource(effect.resourcePath());
        if (source == null) {
            throw new IllegalStateException("Missing bundled audio: " + effect.resourcePath());
        }
        byte[] samples;
        AudioFormat format;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(source)) {
            format = stream.getFormat();
            samples = readAll(stream);
        }
        // Every voice reads the one decoded buffer, so the copies cost lines rather than memory.
        Clip[] voices = new Clip[VOICES_PER_EFFECT];
        for (int voice = 0; voice < voices.length; voice++) {
            Clip clip = AudioSystem.getClip();
            clip.open(format, samples, 0, samples.length);
            voices[voice] = clip;
        }
        return voices;
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int read = stream.read(chunk); read > 0; read = stream.read(chunk)) {
            collected.write(chunk, 0, read);
        }
        return collected.toByteArray();
    }

    @Override
    public void play(SoundFx effect) {
        // Volume is resolved here rather than on the audio thread: it is a plain field read, and
        // passing the value across means the setting is never read from two threads at once.
        double volume = effect.baseVolume() * settings.sfxVolume();
        requests.increment();
        audio.execute(() -> {
            Clip[] pool = clips.get(effect);
            if (pool == null) {
                return;
            }
            // Refused rather than queued once every voice is busy: a shot already fired is not
            // worth hearing late, and holding it would only delay the same pile-up.
            if (liveVoices() >= MAX_VOICES) {
                ceilingDrops.increment();
                return;
            }
            Clip free = firstIdle(pool);
            if (free == null) {
                ceilingDrops.increment();
                return;
            }
            // Set per play, never at open: the SFX slider is read fresh on every call, so baking
            // the gain in at startup would leave the setting doing nothing.
            applyGain(free, volume);
            free.setFramePosition(0);
            free.start();
            started.increment();
        });
    }

    private static Clip firstIdle(Clip[] pool) {
        for (Clip clip : pool) {
            if (!clip.isRunning()) {
                return clip;
            }
        }
        return null;
    }

    private int liveVoices() {
        int live = 0;
        for (Clip[] pool : clips.values()) {
            for (Clip clip : pool) {
                if (clip.isRunning()) {
                    live++;
                }
            }
        }
        return live;
    }

    /**
     * Sets a voice's level from a 0..1 volume.
     *
     * The control is in decibels, so the conversion is logarithmic and silence is the floor rather
     * than minus infinity. A line without the control is left at its own level instead of failing:
     * the wrong volume is better than no sound.
     */
    private static void applyGain(Clip clip, double volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0
                ? gain.getMinimum()
                : (float) (20 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
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

    /** One line of what the effects path has done since launch, for the soak harness and for bugs. */
    public String effectTally() {
        return "requested=" + requests.value()
                + " started=" + started.value()
                + " droppedByCeiling=" + ceilingDrops.value()
                + " droppedByQueue=" + queueDrops.value();
    }

    /** Written on the audio thread and on callers' threads, read from a third. Hence atomic. */
    private static final class Counter {
        private final AtomicLong count = new AtomicLong();

        void increment() {
            count.incrementAndGet();
        }

        long value() {
            return count.get();
        }
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
