package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;

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
        this.world = new World(mode, List.of(pilots.name(1), pilots.name(2)));
        this.renderer = renderer;
        this.input = input;
        this.sounds = sounds;
        this.settings = settings;
        this.pilots = pilots;
        this.highScores = highScores;
        this.saves = saves;
        this.onRoundOver = onRoundOver;
        this.endless = endless;
        this.director = resume == null
                ? new SpawnDirector(random, settings.difficulty(), mode.rules())
                : new SpawnDirector(random, settings.difficulty(), mode.rules(),
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
        if (saves != null) {
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
        for (ShipController controller : controllers) {
            // Rebuilt per controller: sensitivity is per player, and all three values can change
            // under the pause menu mid-round.
            StickTuning tuning = new StickTuning(settings.gamepadDeadzone(),
                    settings.gamepadAnalog(),
                    settings.gamepadSensitivity(controller.ship().playerNumber()));
            controller.apply(input, world, sounds, tuning);
        }

        world.update();
        driveEnemies();
        director.update(world);
        collisions.resolve(world);
        world.sweep();

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
            if (player.isOut()) {
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
            player.addScore(debrief.totalBonus());
            int career = pilots.addCareerScore(player.name(), debrief.totalBonus());
            // Paid here rather than in the garage, so the balance is already banked by the time
            // the bay opens a few phases later and a level's work is spendable the same level.
            pilots.addCredits(player.name(), debrief.credits());
            if (highScores != null) {
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
        int difficultyCooldown = settings.difficulty().enemyFireCooldown();
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
                    sounds);
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
