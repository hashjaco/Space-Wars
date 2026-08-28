package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyCode;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.garage.GarageSession;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.mode.Debrief;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Rank;
import com.hashimjacobs.spacecase.prefs.SaveGames;
import com.hashimjacobs.spacecase.prefs.SaveSlot;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.prefs.Standing;

/**
 * The per-frame sequence: read input, simulate, resolve collisions, remove the dead, draw.
 *
 * Removal is deliberately a separate step that runs after collision handling, never during it.
 *
 * Clearing a level does not drop straight into the next one. The loop runs a short {@link Phase}
 * sequence instead -- fly off the top, read the debrief, then warp -- which is what turns ten levels
 * into ten places rather than one endless wave stream.
 */
public final class GameLoop {

    /** Where the loop is between levels. Everything except FIGHTING is the celebration. */
    private enum Phase { FIGHTING, VICTORY_LAP, DEBRIEF, GARAGE, WARP }

    private static final int VICTORY_LAP_TICKS = 96;
    private static final double VICTORY_LAP_SPEED = 2.5;
    private static final double VICTORY_LAP_ACCELERATION = 0.34;

    /**
     * Shortest the warp can last, at 60 steps a second.
     *
     * A floor rather than a duration: the warp also waits on the next level's art finishing its
     * decode, and this keeps the fade watchable when that comes back immediately.
     */
    private static final int WARP_MINIMUM_TICKS = 78;

    /**
     * Health at or below which the alarm sounds, and how often it repeats.
     *
     * ponytail: the same quarter-health threshold is spelled as a fraction in {@link Hud}. Two
     * literals in two files beats a shared constant nobody else would ever read.
     */
    private static final int LOW_HEALTH = 25;
    private static final int LOW_HEALTH_ALARM_TICKS = 90;

    private final World world;
    private final SpawnDirector director;
    private final CollisionSystem collisions;

    private IntFunction<PadState> sticks;
    private final Renderer renderer;
    private final InputState input;
    private final SoundBank sounds;
    private final Settings settings;
    private final Pilots pilots;
    private final HighScores highScores;
    private final SaveGames saves;
    private final List<ShipController> controllers = new ArrayList<>();
    private final Consumer<RoundResult> onRoundOver;

    /**
     * Read once, here, rather than out of the settings on every tick.
     *
     * It decides enemy fire rate, the spawn cap and the boss scale, so it is simulation input and
     * not a preference -- and reading it per tick meant two machines on different difficulties
     * would have quietly played different games. In a networked run this is whatever the host sent
     * at level start; the pause menu changing it mid-round is a local-only affordance.
     */
    private final Difficulty difficulty;

    /**
     * Whether the simulation may advance to the given tick. Always true for local play, so there is
     * no networked branch anywhere else in this class.
     *
     * A networked game answers false until every peer's intent for that tick has arrived, which is
     * what holds the machines in step. Rendering is outside this and keeps running, so a peer
     * waiting on the network shows a frozen fight rather than a frozen application.
     */
    private IntPredicate canStep = tick -> true;

    /**
     * Where a player's intent for a tick comes from. Null means sample this machine's own keyboard
     * and pad, which is every local game.
     */
    private IntentSource intents;

    /** Told the fingerprint of each finished fight tick, when anybody is listening. */
    private TickObserver tickObserver;

    /**
     * The other machines in this match, or null when there are none.
     *
     * One field rather than a scatter of networked branches: every place this class behaves
     * differently online tests this and nothing else, so the local path is exactly the path it has
     * always been.
     */
    private LevelHandshake handshake;

    /**
     * Which seat this machine's pilot flies, or {@link #EVERY_SEAT_IS_LOCAL} when they all are.
     *
     * Every peer runs the whole simulation, so every machine knows exactly what all four players
     * did. What differs is whose business it is to write any of it down, open a garage bay for, or
     * save. One field answers all three, because they are the same question.
     */
    private int localSeat = EVERY_SEAT_IS_LOCAL;

    /** Single-machine play, where every ship at the keyboard is this pilot's business. */
    private static final int EVERY_SEAT_IS_LOCAL = 0;

    /**
     * Whether this run ignores galaxy borders.
     *
     * False for the campaign, which is what almost every run is: ten levels, then an ending. True
     * only for the endless run unlocked by finishing the campaign, which is where {@code Level.next}
     * wrapping past the last level and the loop escalation on top of it finally get used.
     */
    private final boolean endless;

    private final FixedTimestep timestep = new FixedTimestep();
    private final Map<Integer, Debrief.Tally> levelStart = new HashMap<>();
    private final List<Debrief> debriefs = new ArrayList<>();
    private final List<Standing> standings = new ArrayList<>();

    private AnimationTimer timer;
    private Phase phase = Phase.FIGHTING;
    private int phaseTicks;
    private boolean debriefArmed;
    private boolean paused;
    private boolean finished;
    private boolean bossMusicPlaying;
    private GarageSession garage;
    private SaveSlot levelStartSave;

    /** Toggles the frame-time readout. Held-key edge, so one press flips it rather than sixty. */
    private static final KeyCode FRAME_METER_KEY = KeyCode.F3;
    private static final long METER_WINDOW_NANOS = 1_000_000_000L;

    private boolean showFrameMeter;
    private boolean meterKeyWasDown;
    private long lastFrameNanos;
    private long meterWindowStart;
    private double worstMs;
    private int worstSteps;
    private double shownMs;
    private int shownSteps;

    /** A fresh run from level one, with nothing to restore and nowhere to checkpoint. */
    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Pilots pilots, Random random,
                    Consumer<RoundResult> onRoundOver) {
        this(mode, renderer, input, sounds, settings, pilots, random, onRoundOver, null, null, null);
    }

    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Pilots pilots, Random random,
                    Consumer<RoundResult> onRoundOver,
                    HighScores highScores, SaveGames saves, SaveSlot resume) {
        this(mode, renderer, input, sounds, settings, pilots, random, onRoundOver,
                highScores, saves, resume, false);
    }

    /**
     * @param highScores where per-level bests are recorded; may be null in tests
     * @param saves      where checkpoints are written; may be null in tests
     * @param resume     a saved run or a level-select replay to start from, or null for level one
     * @param endless    true for a post-campaign run that ignores galaxy borders and keeps looping
     */
    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Pilots pilots, Random random,
                    Consumer<RoundResult> onRoundOver,
                    HighScores highScores, SaveGames saves, SaveSlot resume, boolean endless) {
        this(mode, renderer, input, sounds, settings, pilots, random, onRoundOver,
                highScores, saves, resume, endless, mode.rules().playerCount());
    }

    /**
     * @param seats how many ships to field, which an online room decides rather than the mode.
     *              Local play passes {@code mode.rules().playerCount()} and is unchanged.
     */
    public GameLoop(GameMode mode, Renderer renderer, InputState input, SoundBank sounds,
                    Settings settings, Pilots pilots, Random random,
                    Consumer<RoundResult> onRoundOver,
                    HighScores highScores, SaveGames saves, SaveSlot resume, boolean endless,
                    int seats) {
        // Exactly as many names as there are seats. Passing a fixed two was harmless while World
        // trimmed the list to the mode's count; now that the list decides, a fixed two would field
        // a wingman in a solo run.
        this.world = new World(mode, pilotNames(pilots, seats));
        this.renderer = renderer;
        this.input = input;
        this.sounds = sounds;
        this.settings = settings;
        this.pilots = pilots;
        this.highScores = highScores;
        this.saves = saves;
        this.onRoundOver = onRoundOver;
        this.endless = endless;
        this.difficulty = settings.difficulty();
        this.director = resume == null
                ? new SpawnDirector(random, difficulty, mode.rules())
                : new SpawnDirector(random, difficulty, mode.rules(),
                        resume.level(), resume.wavesSurvived(), resume.loop());
        // A resumed run or a level-select replay can start on a level that runs sideways, and only
        // stepWarp() used to say so -- leaving the pilots facing up on a side-view leg.
        world.enterLevel(director.level());
        this.collisions = new CollisionSystem(sounds, random);
        attachControllers();
        fitSavedLoadouts();
        restore(resume);
        snapshotLevelStart();
        snapshotSave();
    }

    /**
     * Puts a saved run's counters back on the ships.
     *
     * After {@link #fitSavedLoadouts()} and never touching the loadout: a replay is for flying an
     * old level in the ship you have now. A level-select replay carries an empty player list, so
     * the loop bound skips it and no separate branch is needed for that case.
     */
    private void restore(SaveSlot resume) {
        if (resume == null) {
            return;
        }
        List<PlayerShip> players = world.players();
        for (int i = 0; i < players.size() && i < resume.players().size(); i++) {
            players.get(i).restore(resume.players().get(i));
        }
    }

    /**
     * Freezes where this level began, for the automatic checkpoint and for a manual save.
     *
     * Kept apart from {@link #snapshotLevelStart()}, which captures the same moment for the
     * debrief to subtract from: one is display arithmetic and the other is persistence, and two
     * adjacent calls is cheaper than one method that does both jobs.
     */
    private void snapshotSave() {
        levelStartSave = new SaveSlot(world.mode(), director.level(), director.wavesSurvived(),
                director.loop(), world.players().stream().map(PlayerShip::progress).toList());
        // Same rule scoreLevel applies to recordClear: an endless run is not campaign progress.
        // It also wraps past the last level, so checkpointing one stored a looped save that the
        // load-time migration then read as "finished the campaign".
        // Not for a networked run either. SaveSlot stores per-player progress positionally, so a
        // guest's checkpoint would put the host's pilot in slot 0 and hand it to them on a solo
        // resume -- and resuming a multiplayer run without the other people is meaningless anyway.
        // Clearing a level still counts: recordClear in scoreLevel is deliberately not guarded.
        if (saves != null && !endless && localSeat == EVERY_SEAT_IS_LOCAL) {
            saves.saveCheckpoint(levelStartSave);
        }
    }

    /** The current level's opening state, which is what the pause menu writes to a slot. */
    public SaveSlot checkpoint() {
        return levelStartSave;
    }

    /**
     * Puts each pilot back in the ship they last flew.
     *
     * Before the first level rather than after it, so upgrades bought on a previous run are already
     * fitted when the run opens rather than arriving at the first garage.
     */
    private void fitSavedLoadouts() {
        for (PlayerShip player : world.players()) {
            String code = pilots.loadoutCode(player.name());
            player.applyLoadout(Loadout.decode(code, player.playerNumber()));
        }
    }

    /**
     * Set after construction rather than passed in: the controllers are built in the constructor,
     * and the pad reader belongs to the router that outlives any one round. The lambda below defers
     * to this field, so a reader arriving later still reaches every ShipController.
     */
    public void setSticks(IntFunction<PadState> sticks) {
        this.sticks = sticks;
    }

    /**
     * How this player's stick should feel right now.
     *
     * Rebuilt per player per tick: sensitivity is per player, and all three values can change under
     * the pause menu mid-round. Purely local -- these three settings are consumed by
     * {@link ShipController#sample} and never reach the simulation, which is what lets two players
     * on different sensitivities fly the same ship.
     */
    private StickTuning tuningFor(int playerNumber) {
        return new StickTuning(settings.gamepadDeadzone(), settings.gamepadAnalog(),
                settings.gamepadSensitivity(playerNumber));
    }

    /**
     * Holds the simulation until the network says a tick may run. Pass null to hand it back.
     *
     * Set alongside {@link #setIntentSource}; a gate without a source would stall, and a source
     * without a gate would read intents that had not arrived.
     */
    public void setStepGate(IntPredicate gate) {
        this.canStep = gate == null ? tick -> true : gate;
    }

    /** Where per-tick intents come from. Null restores sampling this machine's own controls. */
    public void setIntentSource(IntentSource intents) {
        this.intents = intents;
    }

    /**
     * Puts a barrier between levels, so peers that spent different amounts of time in the garage
     * start the next fight together. Null restores single-machine play.
     */
    public void setLevelHandshake(LevelHandshake handshake) {
        this.handshake = handshake;
    }

    /**
     * Names the one seat this machine's pilot flies, for a networked run.
     *
     * Everything downstream of this is bookkeeping rather than simulation: a bay in the garage, a
     * career to credit, a checkpoint to write. The fight itself is identical on every machine
     * whatever this is set to.
     */
    public void setLocalSeat(int playerNumber) {
        this.localSeat = playerNumber;
    }

    /**
     * Fits the ships the loadouts the host named, for the first fight of a networked run.
     *
     * Later levels get theirs through {@link LevelHandshake}; the first one has no barrier before
     * it, and {@code fitSavedLoadouts} has already fitted this machine's own pilot to every seat.
     */
    public void fitLoadouts(java.util.List<String> codes) {
        world.fitLoadouts(codes);
    }

    /**
     * This machine's own input for one seat, right now, read through the same path a local game
     * uses.
     *
     * The one thing a networked game still needs from the keyboard. Everything else about a ship
     * arrives from the wire, but somebody has to produce this machine's contribution, and doing it
     * through {@link ShipController#sample} is what guarantees a player's deadzone and sensitivity
     * feel the same online as off -- those settings are consumed here and never travel.
     *
     * @return that seat's intent, or {@link Intent#NEUTRAL} if this loop has no such seat
     */
    public Intent sampleLocal(int playerNumber) {
        for (ShipController controller : controllers) {
            if (controller.ship().playerNumber() == playerNumber) {
                return controller.sample(input, tuningFor(playerNumber));
            }
        }
        return Intent.NEUTRAL;
    }

    /**
     * Watches each finished fight tick. Null, the default, means no checksum is ever computed.
     */
    public void setTickObserver(TickObserver tickObserver) {
        this.tickObserver = tickObserver;
    }

    /** Whether this machine speaks for that ship's pilot. True for everyone in a local game. */
    private boolean isLocal(PlayerShip player) {
        return localSeat == EVERY_SEAT_IS_LOCAL || player.playerNumber() == localSeat;
    }

    private void attachControllers() {
        List<PlayerShip> players = world.players();
        boolean solo = players.size() == 1;
        for (PlayerShip player : players) {
            PlayerControls controls = PlayerControls.of(settings, player.playerNumber(), solo);
            controllers.add(new ShipController(player, controls,
                    number -> sticks == null ? null : sticks.apply(number)));
        }
    }

    public void start() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                frame(now);
            }
        };
        timer.start();
    }

    /**
     * Advances the simulation by however many fixed steps the elapsed time earned, then draws once.
     * Rendering every frame while stepping at a fixed rate is what keeps the game the same speed on
     * a 60Hz and a 120Hz display.
     */
    private void frame(long frameNanos) {
        if (paused || finished) {
            return;
        }
        int steps = timestep.stepsFor(frameNanos);
        for (int i = 0; i < steps && !finished; i++) {
            // The accumulator has already been drained for these steps, so a tick we decline here
            // is time lost rather than banked. That is the same trade MAX_STEPS_PER_FRAME makes,
            // and the one worth making: the fight hitches, it does not fast-forward afterwards.
            if (!canStep.test(world.tick())) {
                break;
            }
            step();
        }
        renderer.draw(world, director);
        switch (phase) {
            case DEBRIEF -> renderer.drawDebrief(director.level(), debriefs, standings, debriefArmed);
            case GARAGE -> renderer.drawGarage(garage, phaseTicks);
            case WARP -> renderer.drawWarpVeil(warpOpacity());
            default -> {
            }
        }
        trackFrame(frameNanos, steps);
        if (showFrameMeter) {
            renderer.drawFrameMeter(shownMs, shownSteps);
        }
    }

    /**
     * Records the worst frame of each second, not the mean.
     *
     * A run that averages 60fps with one 40ms spike in it reads as smooth on a mean and reads as a
     * stutter to whoever is playing; the spike is the only number worth showing. The step count that
     * came with the worst frame rides along, because a frame that ran several simulation steps was
     * catching up from a stall rather than being slow to draw -- the two want different fixes.
     */
    private void trackFrame(long frameNanos, int steps) {
        if (lastFrameNanos != 0) {
            double elapsedMs = (frameNanos - lastFrameNanos) / 1_000_000.0;
            if (elapsedMs > worstMs) {
                worstMs = elapsedMs;
                worstSteps = steps;
            }
        }
        lastFrameNanos = frameNanos;

        if (frameNanos - meterWindowStart >= METER_WINDOW_NANOS) {
            shownMs = worstMs;
            shownSteps = worstSteps;
            worstMs = 0;
            worstSteps = 0;
            meterWindowStart = frameNanos;
        }

        boolean down = input.isHeld(FRAME_METER_KEY);
        if (down && !meterKeyWasDown) {
            showFrameMeter = !showFrameMeter;
        }
        meterKeyWasDown = down;
    }

    /**
     * Fade to black and back, peaking halfway through the minimum warp.
     *
     * Holds full black past the halfway point when the next level's art is still decoding, so a slow
     * fault-in shows as a longer blackout rather than as a stutter in a half-faded scene.
     */
    private double warpOpacity() {
        double half = WARP_MINIMUM_TICKS / 2.0;
        if (phaseTicks < half) {
            return phaseTicks / half;
        }
        if (!Assets.warmedUp()) {
            return 1;
        }
        double out = (phaseTicks - half) / half;
        double opacity = Math.max(0, 1 - out);
        return opacity;
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
        // A round can end from inside the garage -- quitting to the menu, for one -- and the router
        // outlives this loop because the scene does. Leaving it installed hands every keystroke on
        // the next screen to a garage that is no longer running.
        input.setMenuRouter(null);
    }

    /** One simulation step. Deliberately does no drawing -- {@link #frame} owns that. */
    private void step() {
        switch (phase) {
            case FIGHTING -> stepFight();
            case VICTORY_LAP -> stepVictoryLap();
            case DEBRIEF -> stepDebrief();
            case GARAGE -> stepGarage();
            case WARP -> stepWarp();
        }
    }

    private void stepFight() {
        // Read before the world moves. tickScenery advances the clock inside update(), so by the
        // bottom of this method world.tick() names the *next* tick, not the one that just ran.
        int tick = world.tick();
        for (ShipController controller : controllers) {
            int number = controller.ship().playerNumber();
            Intent intent = intents == null
                    ? controller.sample(input, tuningFor(number))
                    : intents.intentFor(number, tick);
            controller.apply(intent, world, sounds);
        }

        world.update();
        driveEnemies();
        director.update(world);
        collisions.resolve(world);
        world.sweep();

        // After sweep, so the fingerprint is of a settled world, and before the cosmetic calls
        // below, which change nothing a peer could disagree about.
        if (tickObserver != null) {
            tickObserver.afterTick(tick, world.checksum());
        }

        warnLowHealth();
        updateBossMusic();
        if (director.levelCleared()) {
            beginVictoryLap();
            return;
        }
        checkRoundOver();
    }

    /**
     * The flagship is dead: score the level, clear the sky, and let the players run for the exit.
     *
     * Scoring happens here rather than on the debrief screen so the numbers are fixed at the moment
     * the fight ended, before the players are moved anywhere.
     */
    private void beginVictoryLap() {
        phase = Phase.VICTORY_LAP;
        phaseTicks = 0;
        scoreLevel();
        // After scoring, never before: the debrief subtracts a lives tally, so handing a fallen
        // player three lives first would report a level they did not fly as one they survived.
        world.reviveFallenAllies();
        world.clearBattlefield();
        sounds.play(SoundFx.LEVEL_CLEAR);
    }

    /**
     * Players accelerate off the top of the screen.
     *
     * Runs {@link World#tickScenery()} rather than {@code update()} on purpose: a full update expires
     * anything outside the arena, which would delete the ships the moment they left it.
     */
    private void stepVictoryLap() {
        phaseTicks++;
        world.tickScenery();

        double step = VICTORY_LAP_SPEED + phaseTicks * VICTORY_LAP_ACCELERATION;
        for (PlayerShip player : world.players()) {
            if (player.isOut()) {
                continue;
            }
            player.setPosition(player.x() + player.facing().xDirection() * step,
                    player.y() + player.facing().yDirection() * step);
        }
        if (phaseTicks >= VICTORY_LAP_TICKS) {
            phase = Phase.DEBRIEF;
            phaseTicks = 0;
            debriefArmed = false;
            input.clear();
        }
    }

    /**
     * Holds on the stats until a button is pressed.
     *
     * Arms only once every key is up, so the shot that killed the boss cannot also skip the screen it
     * earned.
     */
    private void stepDebrief() {
        phaseTicks++;
        world.tickScenery();

        if (!debriefArmed) {
            debriefArmed = !input.anyHeld();
            return;
        }
        if (!input.anyHeld()) {
            return;
        }
        openGarage();
        // Started here rather than on the way out of the garage: the next level's art now decodes
        // while the pilots shop, so the warp afterwards is almost always instant.
        Level next = director.level().next();
        // Head art too, or a multi-part flagship stutters through a synchronous decode on arrival.
        Assets.preload(next.layers(), next.boss().art(), next.boss().headArt());
    }

    /**
     * Opens the garage for everyone still flying.
     *
     * A pilot who is out gets no bay: they have no lives left, so either a partner is about to
     * revive them on the next level clear or the round is already over, and either way there is
     * nothing for them to spend on right now.
     */
    private void openGarage() {
        List<GarageSession.Seat> seats = new ArrayList<>();
        List<Integer> credits = new ArrayList<>();
        List<Loadout> loadouts = new ArrayList<>();
        for (ShipController controller : controllers) {
            PlayerShip player = controller.ship();
            // A bay for a ship this machine does not fly would be a shop window: its keys are
            // unbound, and its credits are somebody else's to spend.
            if (player.isOut() || !isLocal(player)) {
                continue;
            }
            PlayerControls keys = controller.controls();
            seats.add(new GarageSession.Seat(player.name(), keys.up(), keys.down(),
                    keys.left(), keys.right(), keys.fire()));
            credits.add(pilots.credits(player.name()));
            loadouts.add(player.loadout());
        }
        garage = new GarageSession(seats, credits, loadouts);
        phase = Phase.GARAGE;
        phaseTicks = 0;
        // Takes the keyboard off the ships and clears anything still held, so the button that
        // dismissed the debrief cannot also buy the first upgrade.
        input.setMenuRouter(garage::handleKey);
        sounds.playMusic(MusicCue.GARAGE);
    }

    /**
     * Holds until every pilot has launched, then banks what they bought.
     *
     * Note this can finish on the tick it starts when nobody has a bay -- a session with no seats
     * is done by definition -- which is the behaviour wanted: no bays, nothing to wait for.
     */
    private void stepGarage() {
        phaseTicks++;
        world.tickScenery();
        if (!garage.everyoneDone()) {
            return;
        }
        commitGarage();
        garage = null;
        input.setMenuRouter(null);
        phase = Phase.WARP;
        phaseTicks = 0;
    }

    /** Writes each pilot's spending back to their record and onto the ship they are flying. */
    private void commitGarage() {
        for (int bay = 0; bay < garage.bayCount(); bay++) {
            String pilotName = garage.pilotName(bay);
            Loadout loadout = garage.loadout(bay);
            pilots.setCredits(pilotName, garage.credits(bay));
            pilots.setLoadoutCode(pilotName, loadout.encode());
            for (PlayerShip player : world.players()) {
                if (player.name().equals(pilotName)) {
                    player.applyLoadout(loadout);
                }
            }
        }
    }

    /** Blackout while the next level's art decodes, then hand the ships back to the players. */
    private void stepWarp() {
        phaseTicks++;
        world.tickScenery();
        if (phaseTicks < WARP_MINIMUM_TICKS || !Assets.warmedUp()) {
            return;
        }
        // Every peer reaches this line having spent a different amount of time on the victory lap,
        // the debrief and the garage -- all three end on something local. Nothing below may run
        // until they have agreed on the terms, because world.tick() is simulation input and the
        // loadouts bought in those garages change what each ship can do.
        if (handshake != null && !handshake.readyToFight(director.level().ordinal() + 1, world)) {
            return;
        }

        Galaxy leaving = director.level().galaxy();
        director.advanceLevel();
        // A campaign run is one galaxy. Crossing the border ends it, which is what makes clearing a
        // galaxy an ending rather than a wave counter ticking over -- fifty levels unbroken is a
        // four-hour sitting with no way out but dying. Endless ignores borders and keeps going,
        // which is what the loop counter and its escalation exist for.
        if (!endless && director.level().galaxy() != leaving) {
            finishRound(true);
            return;
        }
        // Before the spawns are restored, since arriving at a level that runs the other way moves
        // where "back of the arena" is.
        world.enterLevel(director.level());
        for (PlayerShip player : world.players()) {
            player.returnToSpawn();
        }
        snapshotLevelStart();
        snapshotSave();
        sounds.playMusic(world.mode().music());
        bossMusicPlaying = false;
        phase = Phase.FIGHTING;
        phaseTicks = 0;
        timestep.reset();
    }

    /** {@link Pilots#name} falls back to "PILOT n" for any seat, so three and four name themselves. */
    private static List<String> pilotNames(Pilots pilots, int seats) {
        List<String> names = new ArrayList<>();
        for (int number = 1; number <= seats; number++) {
            names.add(pilots.name(number));
        }
        return names;
    }

    /** Records where each player's counters stood as a level began, for the debrief to subtract. */
    private void snapshotLevelStart() {
        levelStart.clear();
        for (PlayerShip player : world.players()) {
            levelStart.put(player.playerNumber(), tally(player));
        }
    }

    private static Debrief.Tally tally(PlayerShip player) {
        Debrief.Tally snapshot = new Debrief.Tally(player.enemiesKilled(),
                player.asteroidsDestroyed(), player.shotsFired(), player.shotsHit(),
                player.damageTaken(), player.lives());
        return snapshot;
    }

    /** Pays every surviving player their bonuses and credits their career, recording promotions. */
    private void scoreLevel() {
        debriefs.clear();
        standings.clear();

        for (PlayerShip player : world.players()) {
            Debrief.Tally before = levelStart.get(player.playerNumber());
            Level level = director.level();
            Debrief debrief = Debrief.of(player.name(), level.indexInGalaxy(),
                    level.galaxy().number(), director.parTicks(), before, tally(player),
                    director.ticksIntoLevel());
            debriefs.add(debrief);

            Rank held = pilots.rank(player.name());
            // Not guarded: the score lives on the ship, which is simulation state every machine
            // must agree about. Only the three writes below are this machine's own records.
            player.addScore(debrief.totalBonus());

            // Every peer knows what all four players did, so four machines would otherwise each
            // invent and credit four careers in their own preferences. Each writes down its own
            // pilot and reads everyone else's, which needs no reconciling because the simulation
            // already agreed.
            boolean mine = isLocal(player);
            int career = mine
                    ? pilots.addCareerScore(player.name(), debrief.totalBonus())
                    : pilots.careerScore(player.name());
            if (mine) {
                // Paid here rather than in the garage, so the balance is already banked by the time
                // the bay opens a few phases later and a level's work is spendable the same level.
                pilots.addCredits(player.name(), debrief.credits());
            }
            if (mine && highScores != null) {
                // The level's own bonus, not the running score: a replay of level two and a deep
                // run passing through it have wildly different totals but comparable level work.
                highScores.submit(world.mode(), director.level(), debrief.totalBonus());
            }
            Rank earned = Rank.forCareerScore(career);
            standings.add(new Standing(player.name(), earned, career, earned != held));
        }
        // The flagship is confirmed dead by the time this runs, so this is the one place a level
        // becomes "cleared". Endless runs are not campaign progress and do not unlock anything.
        if (saves != null && !endless) {
            saves.recordClear(world.mode(), director.level());
        }
    }

    /**
     * Beeps while anyone is nearly dead.
     *
     * Retriggered on an interval rather than looped: a looping clip would need a stop call, and
     * with it a decision about every way a fight can end. This carries no state at all, sounds once
     * for the pair rather than once each, and goes quiet by itself the moment health comes back or
     * the loop leaves the fight.
     */
    private void warnLowHealth() {
        if (world.tick() % LOW_HEALTH_ALARM_TICKS != 0) {
            return;
        }
        for (PlayerShip player : world.players()) {
            if (!player.isOut() && player.health() <= LOW_HEALTH) {
                sounds.play(SoundFx.LOW_HEALTH);
                return;
            }
        }
    }

    /** Swaps to the boss track while a boss is on screen, and back to the mode's track after. */
    private void updateBossMusic() {
        boolean bossOnScreen = world.bossPresent();
        if (bossOnScreen == bossMusicPlaying) {
            return;
        }
        bossMusicPlaying = bossOnScreen;
        // The flagship picks its own cue, so the two monsters get their own music instead of the
        // track every warship shares. World.boss() answers with a surviving part when the torso is
        // already gone, which is why this can dereference it -- see there.
        MusicCue track = bossOnScreen ? world.boss().boss().music() : world.mode().music();
        sounds.playMusic(track);
    }

    /**
     * Indexes rather than iterating: a SPAWNER-phase boss adds enemies from inside
     * {@link EnemyWeapons#fire}, which an iterator over the same list would answer with a
     * ConcurrentModificationException. Bounding the loop up front also means escorts are not driven
     * on the tick they arrive.
     */
    private void driveEnemies() {
        int difficultyCooldown = difficulty.enemyFireCooldown();
        int enemyCap = difficulty.maxEnemies(world.players().size());
        List<EnemyShip> enemies = world.enemies();
        for (int i = 0, count = enemies.size(); i < count; i++) {
            EnemyShip enemy = enemies.get(i);
            PlayerShip target = world.nearestPlayer(enemy);
            if (target == null) {
                continue;
            }
            enemy.trackAcross(target);
            if (!enemy.hasEntered()) {
                continue;
            }
            EnemyWeapons.driveWeapons(world, enemy, target, director.level(), difficultyCooldown,
                    enemyCap, sounds);
        }
    }

    private void checkRoundOver() {
        List<PlayerShip> players = world.players();
        long stillIn = players.stream().filter(player -> !player.isOut()).count();

        boolean over = world.rules().lastPlayerStanding()
                ? stillIn <= 1
                : stillIn == 0;
        if (!over) {
            return;
        }
        finishRound(false);
    }

    /**
     * Ends the round, whether it was lost or finished.
     *
     * @param galaxyCleared true when the run ended by reaching the end of its galaxy rather than by
     *                      running out of lives -- the difference between winning and losing
     */
    private void finishRound(boolean galaxyCleared) {
        finished = true;
        stop();
        RoundResult result = RoundResult.of(world, director, galaxyCleared);
        onRoundOver.accept(result);
    }

    /** Pausing is only offered during the fight; the celebration is short and runs itself. */
    public boolean isPausable() {
        boolean pausable = phase == Phase.FIGHTING;
        return pausable;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            renderer.drawPausedVeil();
            input.clear();
            return;
        }
        // Forget the paused interval so it is not replayed as simulation debt on resume, and so the
        // meter does not report the whole pause as one catastrophic frame.
        timestep.reset();
        lastFrameNanos = 0;
    }

    public boolean isPaused() {
        return paused;
    }
}
