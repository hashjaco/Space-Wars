package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Boss;
import com.hashimjacobs.spacecase.entity.BurrowingWorm;
import com.hashimjacobs.spacecase.entity.PilotedMech;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.entity.VoidEntity;
import com.hashimjacobs.spacecase.entity.WaveShip;
import com.hashimjacobs.spacecase.mode.Waves;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.mode.ModeRules;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * Decides what appears and when: asteroids, enemy waves, the level boss, and pickups.
 *
 * Levels advance by killing bosses, not by running a clock: each {@link Level} fields its waves, its
 * boss arrives, and the level only turns over once that boss is dead. Past the last level the run
 * wraps to the first with more spawn pressure, so it stays endless.
 *
 * Detecting a cleared level and moving on are deliberately separate: {@link #levelCleared()} raises
 * the flag and {@link #advanceLevel()} acts on it, so the game loop can run a victory lap and a
 * debrief in between instead of the sky changing mid-flight.
 *
 * The Random is injected rather than taken from Math.random(), so spawn behaviour is reproducible
 * under test.
 */
public final class SpawnDirector {

    /**
     * How long a <em>group</em> may be camped before it turns over anyway. Fifteen seconds.
     *
     * Was twenty-five, back when a wave was one group. A wave is three now, so the old figure was
     * seventy-five seconds of camping per wave and better than six minutes on a wave-six level.
     *
     * This was WAVE_LENGTH_TICKS, back when a wave was only a clock and this was how long every one
     * of them lasted without exception. It is the ceiling now rather than the duration: a wave ends
     * when its ships are gone, and this is what stops a player who parks in a corner and ignores
     * the last survivor from holding a level open forever.
     *
     * Package-private so SpawnDirectorTest can name it rather than repeating the literal it used
     * to hard-code.
     */
    static final int WAVE_TIMEOUT_TICKS = 900;

    /**
     * A brisk wave, for the debrief's reference clear time.
     *
     * Deliberately not WAVE_TIMEOUT_TICKS. Par is what a level ought to take, and since waves now
     * end when they are killed, the timeout is what a level takes when nothing is killed. Paying
     * the debrief's Swift bonus against that would hand a competent player something like twenty
     * thousand points a level -- about two hundred credits into a garage economy that was priced
     * without them.
     *
     * Per <em>group</em>, not per wave -- {@code parTicks} multiplies by {@code GROUPS_PER_WAVE}.
     * It came down from 600 when a wave became three groups: a brisk group is not a brisk wave, and
     * leaving it would have priced par at three times a competent clear and paid the Swift bonus on
     * every level.
     *
     * ponytail: under seven seconds a group is arithmetic, not a measurement. It is the calibration
     * knob for the whole clear-time economy; check it against a real clear before trusting it.
     */
    private static final int WAVE_PAR_TICKS = 400;
    private static final int POWERUP_CHANCE_PER_THOUSAND = 3;

    /** How long the incoming-flagship banner stays up after the boss spawns. */
    private static final int BOSS_WARNING_TICKS = 180;

    /** Boss-fight allowance in a level's reference clear time: a minute, at 60 steps a second. */
    private static final int BOSS_PAR_TICKS = 3600;

    /** Added to the difficulty's per-thousand spawn chances for each full pass through the levels. */
    private static final int LOOP_SPAWN_BONUS = 3;

    private final Random random;
    private final Difficulty difficulty;
    private final ModeRules rules;

    /**
     * This wave's authored ships, while they live.
     *
     * The only entity state the director holds, and it holds it for one wave at a time. Identity is
     * the only way to tell a wave apart from the filler arriving around it, and telling them apart
     * is what makes "the wave is cleared" mean anything.
     */
    private final List<EnemyShip> waveShips = new ArrayList<>();

    private Level level = Level.values()[0];
    private int wavesSurvived = 1;
    private int wavesIntoLevel;
    private int loopsCompleted;
    private int ticksIntoWave;
    /**
     * Which group already went out, as {@code wave * GROUPS_PER_WAVE + group}; -1 before the first.
     *
     * One number rather than a pair because the only question ever asked of it is "is the group we
     * are on already up", and a wave and a group compared separately is two chances to get that
     * wrong.
     */
    private int launchedGroup = -1;

    /** Which of the wave's groups is on the field, counting from zero. */
    private int groupInWave;
    /** Whether that group actually put ships up -- false on a level whose waves are not written. */
    private boolean waveOnField;
    private int ticksIntoLevel;
    private int bossWarningTicks;
    private boolean awaitingBossKill;
    private boolean levelCleared;

    public SpawnDirector(Random random, Difficulty difficulty, ModeRules rules) {
        this.random = random;
        this.difficulty = difficulty;
        this.rules = rules;
    }

    /**
     * Starts a run partway in, from a saved checkpoint or a level-select replay.
     *
     * A constructor rather than setters, so it is structurally impossible to move the level out
     * from under a fight already in progress. Everything the three-argument form leaves at zero
     * stays at zero, which is correct because a checkpoint is only ever taken at a level boundary
     * -- see {@link #advanceLevel()} for the same list of fields being reset.
     *
     * @param loop passes through the levels, counting from one, matching {@link #loop()}
     */
    public SpawnDirector(Random random, Difficulty difficulty, ModeRules rules,
                         Level level, int wavesSurvived, int loop) {
        this(random, difficulty, rules);
        this.level = level;
        this.wavesSurvived = Math.max(1, wavesSurvived);
        this.loopsCompleted = Math.max(0, loop - 1);
    }

    public void update(World world) {
        ticksIntoLevel++;
        if (bossWarningTicks > 0) {
            bossWarningTicks--;
        }
        advanceWaveClock(world);

        if (rules.spawnAsteroids()) {
            maybeSpawnAsteroid(world);
        }
        if (rules.spawnEnemies()) {
            maybeSpawnBoss(world);
            checkLevelCleared(world);
            maybeSpawnEnemy(world);
        }
        // Where enemies exist they drop the pickups; ambient drops are for battle mode, which
        // has no enemies to drop them.
        if (rules.spawnPowerUps() && !rules.spawnEnemies()) {
            maybeSpawnPowerUp(world);
        }
    }

    /**
     * Runs the current wave: launches it, notices when it is dead, and caps how long it may be camped.
     *
     * Waves stop advancing once the boss is on the field, so a player who dodges the fight instead of
     * finishing it cannot inflate the wave counter, and with it the end-of-round score, indefinitely.
     */
    private void advanceWaveClock(World world) {
        if (awaitingBossKill) {
            return;
        }
        // Gated on the rules rather than called from inside the enemy block, because battle mode
        // still counts waves for its end-of-round summary -- it just has nothing to field on one.
        if (rules.spawnEnemies()) {
            launchWaveIfDue(world);
        }
        ticksIntoWave++;
        waveShips.removeIf(ship -> !ship.isAlive());
        // Killed and flown out of the far edge are the same thing here: both leave through kill(),
        // and a wave the player let past is a wave the player survived.
        //
        // Note this asks whether the ships THIS wave ordered are gone, not whether the arena is
        // empty. The filler keeps arriving, so an arena-empty rule would mean every wave ran to its
        // timeout and the feature would not exist. The filler can never hold a wave open.
        boolean cleared = waveOnField && waveShips.isEmpty();
        if (!cleared && ticksIntoWave < WAVE_TIMEOUT_TICKS) {
            return;
        }
        ticksIntoWave = 0;
        waveOnField = false;
        waveShips.clear();
        // A wave is three groups and only the last of them ends it. wavesSurvived stays a count of
        // waves rather than groups on purpose: it feeds the round summary, the save slot and the
        // cruiser gate in pickEnemyKind, and counting groups would silently triple all three.
        if (groupInWave < Waves.GROUPS_PER_WAVE - 1) {
            groupInWave++;
            return;
        }
        groupInWave = 0;
        wavesSurvived++;
        wavesIntoLevel++;
    }

    /**
     * Puts this wave's next group on the field, all at once.
     *
     * Draws nothing from the generator. Every position comes from the authored row, widened by
     * {@code Waves.group}, and the only adjustment is the tunnel lane clamp, which happens after
     * the draw that is not taken -- the same shape as the trickle's, and the reason the seeded
     * spawn tests still describe the game.
     */
    private void launchWaveIfDue(World world) {
        int group = wavesIntoLevel * Waves.GROUPS_PER_WAVE + groupInWave;
        if (launchedGroup == group || wavesIntoLevel >= level.wavesBeforeBoss()) {
            return;
        }
        launchedGroup = group;
        List<List<WaveShip>> waves = Waves.forLevel(level);
        if (wavesIntoLevel >= waves.size()) {
            // Not authored yet, so this wave runs on the clock alone -- which is exactly what every
            // wave in the game did before this existed.
            return;
        }
        Orientation facing = level.orientation();
        for (WaveShip ordered : Waves.group(waves.get(wavesIntoLevel), groupInWave)) {
            Sprite art = level.enemySprite(ordered.kind());
            // From the ordered size, never from the sprite: a ship drawn at 1.4 and placed at 1.0
            // spawns part of itself inside a wall, and the lane clamp below would be measuring the
            // wrong extent while it happened.
            double w = ordered.width(art);
            double h = ordered.height(art);
            double across = insideLane(world, ordered.across() * facing.arenaBreadth(),
                    facing.acrossExtent(w, h));
            double depth = ordered.setback() - facing.alongExtent(w, h);
            EnemyShip ship = new EnemyShip(ordered.kind(), art,
                    facing.atX(depth, across, w, h), facing.atY(depth, across, w, h),
                    difficulty.enemyScale(), ordered);
            // No setVelocity: World.addEnemy stamps enter(orientation), which overwrites one, and
            // trackAcross would overwrite it again next tick. Authored motion rides in on the
            // ship's own MoveStyle and descentSpeed, which are what those two read.
            world.addEnemy(ship);
            waveShips.add(ship);
        }
        waveOnField = !waveShips.isEmpty();
    }

    private void maybeSpawnAsteroid(World world) {
        if (!rolls(escalated(difficulty.asteroidChance()))) {
            return;
        }
        Orientation facing = level.orientation();
        // The random draw order is load-bearing: SpawnDirectorTest runs on a fixed seed.
        int size = random.nextInt(10);
        double extent = switch (size) {
            case 0, 1 -> 88;
            case 2, 3, 4 -> 56;
            default -> 34;
        };
        double across = randomAcross(world, extent);
        double drift = (random.nextDouble() - 0.5) * 1.6;
        double fall = 1.6 + random.nextDouble() * 1.8;

        Asteroid asteroid = switch (size) {
            case 0, 1 -> asteroidAt(facing, Sprite.ASTEROID_HUGE, -90, across, 70, 22, 30);
            case 2, 3, 4 -> asteroidAt(facing, Sprite.ASTEROID_BIG, -60, across, 40, 15, 20);
            default -> asteroidAt(facing, Sprite.ASTEROID_SMALL, -40, across, 20, 9, 10);
        };
        asteroid.setVelocity(facing.vx(fall, drift), facing.vy(fall, drift));
        world.addAsteroid(asteroid);
    }

    private static Asteroid asteroidAt(Orientation facing, Sprite art, double depth, double across,
                                       int health, int contactDamage, int score) {
        double w = art.width();
        double h = art.height();
        return new Asteroid(art, facing.atX(depth, across, w, h), facing.atY(depth, across, w, h),
                health, contactDamage, score);
    }

    private void maybeSpawnEnemy(World world) {
        if (world.enemies().size() >= difficulty.maxEnemies(world.players().size())) {
            return;
        }
        if (!rolls(escalated(difficulty.enemyChance()))) {
            return;
        }
        Orientation facing = level.orientation();
        EnemyShip.EnemyKind kind = pickEnemyKind();
        Sprite art = level.enemySprite(kind);
        double w = art.width();
        double h = art.height();
        double across = randomAcross(world, facing.acrossExtent(w, h));
        double depth = -facing.alongExtent(w, h);
        EnemyShip enemy = new EnemyShip(kind, art,
                facing.atX(depth, across, w, h), facing.atY(depth, across, w, h),
                difficulty.enemyScale());
        world.addEnemy(enemy);
    }

    /**
     * Tougher archetypes become available as the waves progress, and sooner on a harder preset.
     *
     * Still one draw from the generator, whatever the preset shifts: the seeded spawn tests are
     * pinned against this exact stream, and taking a second sample here would re-roll every one of
     * them. The bias moves the thresholds, never the number of draws.
     */
    private EnemyShip.EnemyKind pickEnemyKind() {
        int roll = random.nextInt(100);
        int bias = difficulty.heavyBias();
        if (wavesSurvived >= 3 && roll < 25 + bias) {
            return EnemyShip.EnemyKind.CRUISER;
        }
        if (roll < 55 + bias) {
            return EnemyShip.EnemyKind.FIGHTER;
        }
        return EnemyShip.EnemyKind.SCOUT;
    }

    private void maybeSpawnBoss(World world) {
        if (awaitingBossKill || wavesIntoLevel < level.wavesBeforeBoss()) {
            return;
        }
        awaitingBossKill = true;
        bossWarningTicks = BOSS_WARNING_TICKS;
        Boss flagship = level.boss();
        Orientation facing = level.orientation();
        double w = flagship.art().width();
        double h = flagship.art().height();
        double across = facing.arenaBreadth() / 2 - facing.acrossExtent(w, h) / 2;
        double depth = -facing.alongExtent(w, h);
        // The one place boss difficulty is decided; everything downstream reads it off the ship.
        double scale = difficulty.bossScale(level.number(), loop());
        double x = facing.atX(depth, across, w, h);
        double y = facing.atY(depth, across, w, h);
        // The only place a flagship's class is chosen. Five entries after five galaxies, which is
        // the budget working: everything else in all four is a row of numbers. Tempest's two set
        // pieces are both a second use of a class that already existed rather than a new one --
        // Vaunt in a bigger rig, and the same burrowing animal on a level that runs the other way.
        EnemyShip boss = switch (flagship) {
            case DUNE_LEVIATHAN, STORM_SERPENT -> new BurrowingWorm(flagship, x, y, scale);
            case VAUNT, VAUNT_IN_THE_STORM_RIG -> new PilotedMech(flagship, x, y, scale);
            case AEON -> new VoidEntity(flagship, x, y, scale);
            default -> new EnemyShip(flagship, x, y, scale);
        };
        // Body first, so World.boss() and the HUD find the torso rather than a head.
        world.addEnemy(boss);
        for (EnemyShip part : boss.parts()) {
            world.addEnemy(part);
        }
    }

    /**
     * The boss dying is what ends a level.
     *
     * Raises the flag but does not act on it: {@link #advanceLevel()} does that, once the game loop
     * has run its victory lap and debrief.
     *
     * Safe against a boss that is dead but not yet swept, because the world only reports it absent
     * after {@code World.sweep()}, which runs after this in the frame.
     */
    private void checkLevelCleared(World world) {
        if (!awaitingBossKill || world.bossPresent()) {
            return;
        }
        awaitingBossKill = false;
        levelCleared = true;
    }

    /** Whether the current level's flagship is dead and the level is waiting to turn over. */
    public boolean levelCleared() {
        return levelCleared;
    }

    /** Moves on to the next place. Called by the game loop once it has finished celebrating. */
    public void advanceLevel() {
        levelCleared = false;
        wavesIntoLevel = 0;
        ticksIntoWave = 0;
        ticksIntoLevel = 0;
        waveShips.clear();
        launchedGroup = -1;
        groupInWave = 0;
        waveOnField = false;
        level = level.next();
        if (level == Level.values()[0]) {
            loopsCompleted++;
        }
    }

    private void maybeSpawnPowerUp(World world) {
        if (!rolls(POWERUP_CHANCE_PER_THOUSAND)) {
            return;
        }
        Orientation facing = level.orientation();
        // Battle mode has nothing strong enough to be carrying a beam, so it cannot fall out of an
        // empty sky either.
        PowerUp.Kind kind = PowerUp.Kind.randomCommon(random);
        double w = kind.sprite().width();
        double h = kind.sprite().height();
        double across = spawnLaneForPickup(world, facing, w, h);
        PowerUp powerUp = new PowerUp(kind,
                facing.atX(-40, across, w, h), facing.atY(-40, across, w, h));
        world.addPowerUp(powerUp);
    }

    /**
     * In battle mode pickups drop down the middle so both players have an equal claim; otherwise
     * anywhere across the arena.
     */
    private double spawnLaneForPickup(World world, Orientation facing, double width, double height) {
        if (!rules.lastPlayerStanding()) {
            return randomAcross(world, facing.acrossExtent(width, height));
        }
        double centreBand = facing.arenaBreadth() / 3;
        double offset = random.nextDouble() * centreBand;
        return centreBand + offset;
    }

    /**
     * A position across the lane, leaving room for something this wide. One random draw.
     *
     * In a tunnel the drawn position is pulled into the open lane afterwards, rather than drawn from
     * a narrower range. That distinction is load-bearing: the draw order and the values consumed
     * from the generator stay exactly as they were, so the fixed-seed run every spawn test is pinned
     * against is unchanged. Drawing differently would have re-rolled the whole game.
     *
     * The lane is read at the entry edge, which is where a spawn appears. Enemies are pushed clear
     * every tick after that, and asteroids that drift into rock are culled, so nothing needs the
     * lane to be tracked as it descends.
     */
    private double randomAcross(World world, double acrossExtent) {
        double span = level.orientation().arenaBreadth() - acrossExtent;
        return insideLane(world, random.nextDouble() * span, acrossExtent);
    }

    /**
     * Pulls a position across the lane into the open part of a tunnel. Takes no draw.
     *
     * Extracted so the authored waves and the trickle share one rule, with the generator call left
     * where it was in {@link #randomAcross}. An authored position that needs this has been written
     * outside the band the tunnel guarantees, and the clamp will put it against the wall rather
     * than where its wave drew it -- legal, but not the formation anybody authored. WavesTest is
     * what keeps that from happening quietly.
     */
    private double insideLane(World world, double across, double acrossExtent) {
        Terrain terrain = world.terrain();
        if (terrain.isEmpty()) {
            return across;
        }
        double low = terrain.laneLow(0);
        double high = terrain.laneHigh(0) - acrossExtent;
        if (high < low) {
            return across;
        }
        return Math.max(low, Math.min(high, across));
    }

    private boolean rolls(int chancePerThousand) {
        boolean hit = random.nextInt(1000) < chancePerThousand;
        return hit;
    }

    /** The chosen difficulty's spawn chance, raised once per completed pass through the levels. */
    private int escalated(int chancePerThousand) {
        int raised = chancePerThousand + loopsCompleted * LOOP_SPAWN_BONUS;
        return raised;
    }

    /** Waves cleared across the whole run, never reset. What the end-of-round summary reports. */
    public int wavesSurvived() {
        return wavesSurvived;
    }

    /**
     * Which wave of the current level is being fought, counting from one.
     *
     * Clamped, because {@code wavesIntoLevel} is the count of waves <em>finished</em> and it has to
     * be allowed to reach {@code wavesBeforeBoss} -- that is what {@code launchWaveIfDue},
     * {@code maybeSpawnBoss} and {@code parTicks} all read. Unclamped, the HUD spent every flagship
     * fight showing WAVE 6/5, since the counter freezes there for the whole of it.
     */
    public int waveInLevel() {
        int position = Math.min(wavesIntoLevel + 1, level.wavesBeforeBoss());
        return position;
    }

    /**
     * Which group of the current wave is on the field, counting from one.
     *
     * The sibling of {@link #waveInLevel}. A wave is {@code Waves.GROUPS_PER_WAVE} of these, sent
     * one at a time and each on the last one's clear, so this is the only way from outside to tell
     * a wave that is part-fought from one that has not started.
     */
    public int groupInWave() {
        int position = groupInWave + 1;
        return position;
    }

    /** Ticks spent in the current level, for the debrief's clear-time bonus. */
    public int ticksIntoLevel() {
        return ticksIntoLevel;
    }

    /** Reference clear time for the current level: its wave clock plus an allowance for the boss. */
    public int parTicks() {
        int par = level.wavesBeforeBoss() * Waves.GROUPS_PER_WAVE * WAVE_PAR_TICKS + BOSS_PAR_TICKS;
        return par;
    }

    /** Passes completed through the whole run, counting from one. */
    public int loop() {
        int pass = loopsCompleted + 1;
        return pass;
    }

    /** Whether the incoming-flagship banner should still be up. */
    public boolean bossWarning() {
        boolean warning = bossWarningTicks > 0;
        return warning;
    }

    public Level level() {
        return level;
    }
}
