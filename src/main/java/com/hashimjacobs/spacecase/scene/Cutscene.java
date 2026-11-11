package com.hashimjacobs.spacecase.scene;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.MusicTrack;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * The story: an intro, one scene at each galaxy's border, and an ending, voiced.
 *
 * Each scene is a motion comic in a 16:9 band: shot N is {@code /cutscenes/<scene>/N.jpg}, panned
 * and zoomed slowly, with {@code N.mp4} played over it where one exists. The speaker and their line
 * sit in the black bar underneath.
 *
 * <b>One audio file per scene.</b> {@code scene.mp3} is the dialogue already mixed over its ducked
 * score, and {@code timeline.txt} says when each shot and line starts -- both built offline by
 * {@code tools/cutscenes/mix.py} from the screenplay in {@code tools/cutscenes/script/}. One player
 * per scene rather than one per line, because building a MediaPlayer is the thing that hangs (see
 * {@code SoundBank}); clips add at most one more, disposed when their shot ends.
 *
 * If the scene audio cannot play, the timeline runs on its own clock under the plain score, so the
 * story still reads with its captions.
 *
 * A galaxy scene is named after the galaxy just cleared: {@link #after(Galaxy)}.
 */
public enum Cutscene {

    INTRO("Lights Out", "the beginning", MusicTrack.KESTREL_STATION),
    VERDANCE("The Nest", "after Verdance", MusicTrack.THE_NEST),
    ASHFALL("The Ledger", "after Ashfall", MusicTrack.THE_LEDGER),
    CRYONIS("The Wall", "after Cryonis", MusicTrack.THE_WALL),
    TEMPEST("The Storm Breaks", "after Tempest", MusicTrack.THE_STORM_BREAKS),
    ENDING("Morning", "after Null", MusicTrack.MORNING_AFTER_THE_DARK);

    /** The band the shots play in: the full width at 16:9, letterboxed on the 996x864 stage. */
    private static final double BAND_HEIGHT = Math.round(GameConfig.WIDTH * 9 / 16);
    /** How long a line stays up after it has been spoken, unless the next one replaces it. */
    private static final double LINGER = 1.2;

    /** Who is speaking, as shown, and the color their name is shown in. */
    private static final Map<String, String> NAMES = Map.of(
            "CASE", "CASE", "HOLLIS", "HOLLIS", "VAUNT", "VAUNT",
            "EMPRESS", "THE FROZEN EMPRESS", "AEON", "AEON");
    private static final Map<String, Color> COLORS = Map.of(
            "CASE", Tokens.BRAND, "HOLLIS", Color.web("#f0b44c"), "VAUNT", Color.web("#e0485a"),
            "EMPRESS", Color.web("#7fe0f0"), "AEON", Color.web("#a98bff"));

    private final String title;
    private final String when;
    private final MusicTrack music;

    Cutscene(String title, String when, MusicTrack music) {
        this.title = title;
        this.when = when;
        this.music = music;
    }

    public String title() {
        return title;
    }

    /** Where in the campaign it plays, for the Story screen's second line. */
    public String when() {
        return when;
    }

    /** The scene's score, on its own: the Soundtrack screen's copy, and the fallback under the captions. */
    public MusicTrack music() {
        return music;
    }

    /** The scene that plays once this galaxy is cleared; the last galaxy's is the ending. */
    public static Cutscene after(Galaxy cleared) {
        return cleared.next() == null ? ENDING : valueOf(cleared.name());
    }

    /** The galaxy whose clearing unlocks this scene, or null for the intro. */
    public Galaxy unlockedBy() {
        return switch (this) {
            case INTRO -> null;
            case ENDING -> Galaxy.NULL;
            default -> Galaxy.valueOf(name());
        };
    }

    URL resource(String file) {
        return Cutscene.class.getResource("/cutscenes/" + name().toLowerCase(Locale.ROOT) + "/" + file);
    }

    URL resource(int shot, String extension) {
        return resource(shot + "." + extension);
    }

    /**
     * Where the clips live when they are not in the jar, which is every shipped build: 74 MB of
     * video is most of an installer, so only the stills, the audio and the timeline ship, and the
     * clips are fetched once into {@link #cacheDir()}. A scene with no clip yet plays its stills.
     */
    private static final String CLIPS_BASE = System.getProperty("cutscenes.base",
            "https://github.com/hashjaco/Space-Wars/releases/download/media-1/");

    /** Per-user and outside the install, so an update does not fetch them again. */
    static Path cacheDir() {
        String override = System.getProperty("cutscenes.cache");
        if (override != null) {
            return Path.of(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("mac")) {
            return home.resolve("Library/Application Support/SpaceCase/cutscenes");
        }
        String local = System.getenv("LOCALAPPDATA");
        if (os.contains("win") && local != null) {
            return Path.of(local, "SpaceCase", "cutscenes");
        }
        return home.resolve(".cache/spacecase/cutscenes");
    }

    /** The clip's name in the cache and on the release, which are both flat: {@code intro-3.mp4}. */
    private String clipName(int shot) {
        return name().toLowerCase(Locale.ROOT) + "-" + shot + ".mp4";
    }

    /** A shot's clip: bundled in a development tree, otherwise the fetched copy, otherwise null. */
    URL clip(int shot) {
        URL bundled = resource(shot, "mp4");
        Path cached = cacheDir().resolve(clipName(shot));
        if (bundled != null || !Files.isRegularFile(cached)) {
            return bundled;
        }
        try {
            return cached.toUri().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * Fetches every clip this machine does not have yet, in story order, off the game's threads.
     *
     * Quiet on purpose: offline, or the host down, means stills this session and another try on
     * the next launch. Written to a temporary name and moved, so a half-fetched clip is never played.
     */
    public static void fetchClips() {
        CompletableFuture.runAsync(() -> {
            try (HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL).build()) {
                Path dir = Files.createDirectories(cacheDir());
                for (Cutscene scene : values()) {
                    for (int shot : scene.timeline().shots().stream().map(Shot::number).distinct().toList()) {
                        Path target = dir.resolve(scene.clipName(shot));
                        if (scene.resource(shot, "mp4") != null || Files.exists(target)) {
                            continue;
                        }
                        Path partial = dir.resolve(scene.clipName(shot) + ".part");
                        HttpRequest request = HttpRequest.newBuilder(URI.create(CLIPS_BASE + scene.clipName(shot)))
                                .timeout(java.time.Duration.ofMinutes(5)).build();
                        if (http.send(request, HttpResponse.BodyHandlers.ofFile(partial)).statusCode() == 200) {
                            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
                        } else {
                            Files.deleteIfExists(partial);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Stills this session; the next launch tries again.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    record Shot(double start, int number) {
    }

    record Line(double start, double length, String speaker, String text) {
    }

    record Timeline(List<Shot> shots, List<Line> lines, double end) {
    }

    /** Reads {@code timeline.txt}; see {@code mix.py} for the format. */
    Timeline timeline() {
        List<Shot> shots = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        double end = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                resource("timeline.txt").openStream(), StandardCharsets.UTF_8))) {
            for (String row; (row = in.readLine()) != null; ) {
                String[] f = row.split(" ", 5);
                switch (f[0]) {
                    case "S" -> shots.add(new Shot(Double.parseDouble(f[1]), Integer.parseInt(f[2])));
                    case "L" -> lines.add(new Line(Double.parseDouble(f[1]), Double.parseDouble(f[2]), f[3], f[4]));
                    case "E" -> end = Double.parseDouble(f[1]);
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Timeline(List.copyOf(shots), List.copyOf(lines), end);
    }

    /** A built scene: its root, and the keys it answers (Enter/Space/click next shot, Escape skip). */
    record Screen(Parent root, Predicate<KeyCode> keys) {
    }

    /** Builds the scene and starts it, and runs {@code onDone} once, when it ends or is skipped. */
    Screen play(SoundBank sounds, Runnable onDone) {
        Player player = new Player(timeline(), sounds, onDone);
        player.start();
        return new Screen(player.root, player::key);
    }

    private final class Player {
        final Timeline timeline;
        final SoundBank sounds;
        final Runnable onDone;
        final StackPane root = new StackPane();
        final ImageView still = new ImageView();
        final MediaView film = new MediaView();
        final Pane band = new Pane(still, film);
        final Text speaker = MenuScreen.caption("", Tokens.SIZE_LABEL, Color.WHITE);
        final Text caption = MenuScreen.caption("", Tokens.SIZE_HEADING, Color.WHITE);
        final AnimationTimer clock;
        MediaPlayer audio;
        MediaPlayer clip;
        ParallelTransition drift;
        long startedAt;
        double offset;
        int shot = -1;
        Line showing;
        boolean finished;

        Player(Timeline timeline, SoundBank sounds, Runnable onDone) {
            this.timeline = timeline;
            this.sounds = sounds;
            this.onDone = onDone;
            root.setStyle("-fx-background-color: black;");
            root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);

            band.setPrefSize(GameConfig.WIDTH, BAND_HEIGHT);
            band.setMaxSize(GameConfig.WIDTH, BAND_HEIGHT);
            band.setClip(new Rectangle(GameConfig.WIDTH, BAND_HEIGHT));
            still.setFitWidth(GameConfig.WIDTH);
            still.setFitHeight(BAND_HEIGHT);
            film.setFitWidth(GameConfig.WIDTH);
            film.setFitHeight(BAND_HEIGHT);

            caption.setWrappingWidth(GameConfig.WIDTH - 2 * 80);
            caption.setTextAlignment(TextAlignment.CENTER);
            VBox subtitle = new VBox(Tokens.GAP_XS, speaker, caption);
            subtitle.setAlignment(Pos.CENTER);
            StackPane captionBar = new StackPane(subtitle);
            captionBar.setMaxHeight((GameConfig.HEIGHT - BAND_HEIGHT) / 2);
            StackPane.setAlignment(captionBar, Pos.BOTTOM_CENTER);

            Text hint = MenuScreen.caption("Enter next    Esc skip", Tokens.SIZE_CAPTION, Tokens.TEXT_GHOST);
            StackPane.setAlignment(hint, Pos.TOP_RIGHT);
            StackPane.setMargin(hint, new Insets(Tokens.GAP * 2));

            root.getChildren().addAll(band, captionBar, hint);
            root.setOnMouseClicked(event -> next());
            clock = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    tick(now);
                }
            };
        }

        /**
         * Starts the audio, and the clock once the audio is actually playing, so a slow decoder start
         * cannot put the captions ahead of the voices. A second's grace, then the clock goes anyway.
         */
        void start() {
            sounds.stopMusic();
            showShot(timeline.shots().isEmpty() ? 1 : timeline.shots().get(0).number());
            try {
                audio = new MediaPlayer(new Media(resource("scene.mp3").toExternalForm()));
                audio.setOnError(this::fallBack);
                audio.setOnPlaying(this::begin);
                audio.play();
            } catch (RuntimeException unplayable) {
                fallBack();
            }
            javafx.animation.PauseTransition grace = new javafx.animation.PauseTransition(Duration.seconds(1));
            grace.setOnFinished(event -> begin());
            grace.play();
        }

        private void begin() {
            if (startedAt != 0 || finished) {
                return;
            }
            startedAt = System.nanoTime();
            clock.start();
        }

        /** The scene audio will not play: keep the timeline running under the score alone. */
        private void fallBack() {
            dropAudio();
            if (!finished) {
                sounds.playTrack(music);
                begin();
            }
        }

        private double now(long nanos) {
            return offset + (nanos - startedAt) / 1e9;
        }

        private void tick(long nanos) {
            double t = now(nanos);
            if (t >= timeline.end()) {
                finish();
                return;
            }
            int current = 0;
            for (Shot s : timeline.shots()) {
                if (s.start() <= t) {
                    current = s.number();
                }
            }
            if (current != shot) {
                showShot(current);
            }
            Line line = null;
            for (Line l : timeline.lines()) {
                if (l.start() <= t && t < l.start() + l.length() + LINGER) {
                    line = l;
                }
            }
            if (line != showing) {
                showLine(line);
            }
        }

        boolean key(KeyCode code) {
            switch (code) {
                case ENTER, SPACE -> next();
                case ESCAPE -> finish();
                default -> {
                    return false;
                }
            }
            return true;
        }

        /** Jumps to the next shot's start, or to the end from the last one. */
        void next() {
            if (finished) {
                return;
            }
            double t = now(System.nanoTime());
            double target = timeline.end();
            for (Shot s : timeline.shots()) {
                if (s.start() > t + 0.05) {
                    target = s.start();
                    break;
                }
            }
            offset += target - t;
            if (audio != null) {
                audio.seek(Duration.seconds(target));
            }
        }

        private void showShot(int number) {
            shot = number;
            dropClip();
            still.setImage(new Image(resource(number, "jpg").toExternalForm()));
            fadeIn(band, 500);
            double length = shotLength(number);
            drift(number, length);
            URL movie = clip(number);
            if (movie == null) {
                return;
            }
            try {
                MediaPlayer playing = new MediaPlayer(new Media(movie.toExternalForm()));
                clip = playing;
                playing.setMute(true);
                // A backstop only -- clips are generated at their shot's length. One that is short
                // is slowed toward it, down to half speed.
                playing.setOnReady(() -> playing.setRate(Math.max(0.5,
                        Math.min(1, playing.getMedia().getDuration().toSeconds() / length))));
                // Held on its last frame when it ends. Left alone, an ended player stops showing it
                // and the shot jumps -- which played as the clip looping under the dialogue.
                playing.setOnEndOfMedia(playing::pause);
                film.setMediaPlayer(playing);
                film.setVisible(false);
                double cutAt = shotStart(number);
                playing.setOnPlaying(() -> {
                    film.setVisible(true);
                    // Caught up to the scene clock, so a lip-synced mouth lands on its voice even
                    // when the player took a moment to start.
                    double late = startedAt == 0 ? 0 : now(System.nanoTime()) - cutAt;
                    if (late > 0.15) {
                        playing.seek(Duration.seconds(late * playing.getRate()));
                    }
                });
                // The still is already up and drifting; a clip that fails just leaves it there.
                clip.setOnError(this::dropClip);
                clip.play();
            } catch (RuntimeException unplayable) {
                dropClip();
            }
        }

        private double shotStart(int number) {
            for (Shot s : timeline.shots()) {
                if (s.number() == number) {
                    return s.start();
                }
            }
            return 0;
        }

        private double shotLength(int number) {
            List<Shot> shots = timeline.shots();
            for (int i = 0; i < shots.size(); i++) {
                if (shots.get(i).number() == number) {
                    double end = i + 1 < shots.size() ? shots.get(i + 1).start() : timeline.end();
                    return end - shots.get(i).start();
                }
            }
            return 6;
        }

        private void showLine(Line line) {
            showing = line;
            if (line == null) {
                speaker.setText("");
                caption.setText("");
                return;
            }
            speaker.setText(NAMES.getOrDefault(line.speaker(), line.speaker()));
            speaker.setFill(COLORS.getOrDefault(line.speaker(), Tokens.TEXT_DIM));
            caption.setText(line.text());
            fadeIn(caption, 250);
        }

        /** A slow push in, panning one way on odd shots and the other on even ones, over the shot's length. */
        private void drift(int number, double seconds) {
            if (drift != null) {
                drift.stop();
            }
            still.setScaleX(1);
            still.setScaleY(1);
            still.setTranslateX(0);
            Duration span = Duration.seconds(Math.max(4, seconds) + 1);
            ScaleTransition zoom = new ScaleTransition(span, still);
            zoom.setToX(1.08);
            zoom.setToY(1.08);
            TranslateTransition pan = new TranslateTransition(span, still);
            pan.setToX(number % 2 == 0 ? 24 : -24);
            drift = new ParallelTransition(zoom, pan);
            drift.play();
        }

        private void fadeIn(Node node, double millis) {
            FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }

        private void dropClip() {
            film.setVisible(false);
            film.setMediaPlayer(null);
            if (clip != null) {
                clip.stop();
                clip.dispose();
                clip = null;
            }
        }

        private void dropAudio() {
            if (audio != null) {
                audio.stop();
                audio.dispose();
                audio = null;
            }
        }

        void finish() {
            if (finished) {
                return;
            }
            finished = true;
            clock.stop();
            if (drift != null) {
                drift.stop();
            }
            dropClip();
            dropAudio();
            onDone.run();
        }
    }
}
