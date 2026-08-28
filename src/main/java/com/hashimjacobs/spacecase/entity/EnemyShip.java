package com.hashimjacobs.spacecase.entity;

import java.util.ArrayList;
import java.util.List;

import com.hashimjacobs.spacecase.asset.BossArt;
import com.hashimjacobs.spacecase.asset.Sprite;

/** An AI ship. Drifts down the arena, tracks the nearest player across it, and fires on a timer. */
public class EnemyShip extends Entity {

    /** How far in a boss advances before it stops and holds station to fight. */
    private static final double BOSS_HOLD_DEPTH = 90;

    /**
     * How fast a flagship comes in, and how fast it slides to stay over you.
     *
     * The descent was 0.35, which put twelve seconds between a flagship appearing and it reaching
     * its station -- long enough that the arrival read as a loading pause rather than as a threat.
     * At 1.10 a Sentinel is in position in under four seconds. The clamp in {@link #update()} is
     * what makes this safe to raise: it bounds the overshoot at a single tick whatever the speed,
     * so the boss still stops at BOSS_HOLD_DEPTH rather than sailing past it.
     *
     * The track speed is deliberately still far under {@code GameConfig.PLAYER_SPEED}. It is a
     * bang-bang law on a signum, so raising it moves how fast the flagship corrects and not where
     * it settles -- a player who keeps moving is never cornered by it.
     *
     * Neither applies to the five set-piece flagships. BurrowingWorm, PilotedMech, VoidEntity,
     * BossHead and MechPart all override update() without calling super and drive themselves off
     * their own cycle constants; those were shortened to match, each in its own class.
     */
    private static final double BOSS_DESCENT_SPEED = 1.10;
    private static final double BOSS_TRACK_SPEED = 2.10;

    /**
     * Ceiling on how far a difficulty preset may speed an ordinary enemy up.
     *
     * A scout already crosses the lane at 2.5 against the player's 5.5, and half of that again is
     * about where a wave stops being something you fly through and starts being something that
     * catches you. Hull keeps scaling past this; speed does not.
     */
    private static final double ENEMY_SPEED_SCALE_CAP = 1.5;

    /**
     * How long a floating health bar stays up after the last hit that reached this hull.
     *
     * Three seconds at the fixed sixty-step second. Written as a plain number rather than read off
     * engine.FixedTimestep because entity never imports engine, which is the same reason
     * GameConfig spells its own tick budgets out -- REPAIR_CALM_TICKS is this same 180.
     */
    private static final int HIT_BAR_TICKS = 180;

    /** The last half second of that, spent fading rather than blinking off. */
    private static final int HIT_BAR_FADE_TICKS = 30;

    /** Grace before a flagship's first rocket salvo, so it does not open with one on arrival. */
    private static final int ROCKET_WARMUP_TICKS = 240;

    /**
     * Share of a multi-part flagship's health carried by its parts rather than its body.
     *
     * The tuning knob for the whole fight. Raise it and the heads outlast the torso; lower it and
     * they are a formality on the way to the real target.
     * ponytail: one constant rather than per-boss data, until a second multi-part boss disagrees.
     */
    private static final double PART_HEALTH_SHARE = 0.45;

    private final EnemyKind kind;
    private final Boss boss;
    private final int maxHealth;
    private final double trackSpeed;
    private final int scoreValue;
    private final double scale;
    private final double descentSpeed;
    private final MoveStyle move;
    private final double fireFactor;
    private final int shots;
    private int weavePhase;
    /** Ticks alive, the clock the swerve is drawn against. */
    private int age;
    /** Parts of a multi-part flagship. Empty for every other ship, so every loop over it is free. */
    private final List<EnemyShip> parts = new ArrayList<>();
    private Orientation orientation = Orientation.TOP_DOWN;
    private int health;
    private int fireCooldown;
    /** Ticks left on the floating health bar. Named for PlayerShip.hitFlashTicks, which is this. */
    private int hitBarTicks;
    private int rocketCooldown = ROCKET_WARMUP_TICKS;

    /**
     * An ordinary enemy.
     *
     * The art is passed in rather than read off the archetype: every level fields its own faction, so
     * a scout's stats are fixed while its hull is whatever the current {@code mode.Level} supplies.
     */
    public EnemyShip(EnemyKind kind, Sprite art, double x, double y) {
        this(kind, art, x, y, 1);
    }

    /**
     * An ordinary enemy at the chosen preset's strength.
     *
     * @param scale from {@code prefs.Difficulty.enemyScale}. Multiplies hull outright and speed only
     *              up to {@link #ENEMY_SPEED_SCALE_CAP} -- the same split the flagships use, and for
     *              the same reason: health can climb forever because the answer to it is to keep
     *              shooting, but a hostile that closes faster than the player can leave has no
     *              answer at all.
     */
    public EnemyShip(EnemyKind kind, Sprite art, double x, double y, double scale) {
        this(kind, art, x, y, scale, WaveShip.at(kind, 0));
    }

    /**
     * An ordinary enemy at the preset's strength, tuned as the wave that ordered it asked.
     *
     * @param ordered what this ship's wave row asked to be different about it. An all-defaults
     *                {@link WaveShip} reproduces the archetype exactly, which is what every other
     *                constructor here passes and what keeps the trickle behaving as it always has.
     */
    public EnemyShip(EnemyKind kind, Sprite art, double x, double y, double scale,
                     WaveShip ordered) {
        super(art, x, y, ordered.width(art), ordered.height(art));
        this.kind = kind;
        this.boss = null;
        this.maxHealth = (int) Math.round(kind.health() * scale * ordered.health());
        this.scale = scale;
        // One cap on the product, not one each. The reason ENEMY_SPEED_SCALE_CAP exists -- a
        // hostile that closes faster than the player can leave has no answer at all -- does not
        // care which of the two multipliers got it there. Slowing down is never capped.
        double quickening = Math.min(scale * ordered.speed(), ENEMY_SPEED_SCALE_CAP);
        this.descentSpeed = kind.descentSpeed() * quickening;
        this.trackSpeed = kind.trackSpeed() * quickening;
        this.move = ordered.move();
        this.fireFactor = kind.fireFactor() * ordered.fireGap();
        this.shots = ordered.shots();
        // Deliberately the archetype's, unscaled, matching the flagship constructor and
        // Difficulty.enemyScale: making a ship harder and paying more for it are separate
        // decisions, and tying them would make the hardest wave the fastest way to farm a score.
        this.scoreValue = kind.scoreValue();
        this.health = this.maxHealth;
        // Stagger the first shot so a wave spawning together does not fire in unison.
        this.fireCooldown = kind.ordinal() * 7;
        // Same idea for the swerve, but keyed on where it came in rather than on what it is: a pair
        // of scouts sharing an archetype must not share a phase, or two ships trace one line.
        // Restamped in enter() once the lane is known -- see there for why this one is provisional.
        this.weavePhase = Math.floorMod((int) (x + y), MoveStyle.PERIOD_TICKS);
        setVelocity(0, descentSpeed);
    }

    /** A level's flagship at its authored strength. */
    public EnemyShip(Boss boss, double x, double y) {
        this(boss, x, y, 1);
    }

    /**
     * A level's flagship. Its size, score and armament all come from the {@link Boss}.
     *
     * @param scale how much tougher than authored this one is, from
     *              {@code prefs.Difficulty.bossScale}. Multiplies health here and fire rate and
     *              projectile damage in {@code engine.EnemyWeapons}. Score is deliberately left
     *              unscaled: paying more for the same flagship is a separate decision from making
     *              it harder, and the debrief already pays a bounty that rises with the level.
     */
    public EnemyShip(Boss boss, double x, double y, double scale) {
        this(boss, x, y, scale, boss.art().width(), boss.art().height(),
                (int) Math.round(boss.health() * scale * bodyShare(boss)), boss.scoreValue());
        setVelocity(0, BOSS_DESCENT_SPEED);
        // A multi-part flagship builds its own parts, so no caller can construct half of one.
        // They are added to the world by SpawnDirector, which is the only thing that can.
        for (int index = 0; index < boss.heads(); index++) {
            int share = (int) Math.round(boss.health() * scale * PART_HEALTH_SHARE / boss.heads());
            parts.add(new BossHead(this, index, boss.heads(), share));
        }
    }

    /** The torso's cut of a multi-part flagship's health; all of it for an ordinary one. */
    private static double bodyShare(Boss boss) {
        return boss.heads() == 0 ? 1 : 1 - PART_HEALTH_SHARE;
    }

    /** One part of a larger flagship: its own box and health, sharing the parent's {@link Boss}. */
    protected EnemyShip(Boss boss, double x, double y, double scale,
                        double width, double height, int health, int scoreValue) {
        super(Sprite.BOSS, x, y, width, height);
        this.kind = null;
        this.boss = boss;
        this.scale = scale;
        this.descentSpeed = BOSS_DESCENT_SPEED;
        this.move = MoveStyle.HUNT;
        // A flagship fires its BossPhase, not an archetype's gun, so neither of these is ever read.
        this.fireFactor = 1;
        this.shots = 1;
        this.weavePhase = 0;
        this.maxHealth = health;
        this.trackSpeed = BOSS_TRACK_SPEED;
        this.scoreValue = scoreValue;
        this.health = health;
    }

    /**
     * A boss takes station near the top of the arena rather than drifting out of the bottom, where
     * the world would cull it mid-fight -- and, because killing a boss is what advances the level,
     * hand out the next level for free.
     *
     * The clamp lives here rather than in the velocity setter so no caller can skip it. It used to
     * sit in a helper reached only from {@link #trackAcross}, which meant a boss that was never
     * driven -- because no player was left alive to track -- kept descending.
     */
    @Override
    public void update() {
        super.update();
        age++;
        if (isBoss() && orientation.depth(x(), y(), width(), height()) > BOSS_HOLD_DEPTH) {
            // Stop advancing but keep whatever sideways drift the tracker gave us.
            // ponytail: leaves up to one tick of overshoot (0.35px, once) where the old code
            // snapped the position back. Use Orientation.atX/atY if that ever shows.
            double across = orientation.across(velocityX(), velocityY());
            setVelocity(orientation.vx(0, across), orientation.vy(0, across));
        }
    }

    /**
     * Drives this ship for a tick: how far across the lane it slides, and how far down it advances.
     *
     * Sideways in a top-down level, up and down in a side view -- the same manoeuvre either way,
     * which is why this is one method rather than two. Both numbers come from the ship's
     * {@link MoveStyle}, which is the only thing that decides them; this method's whole job is to
     * ask, and then to project the answer through the {@link Orientation}.
     *
     * The advance is passed in rather than recomputed, and that is load-bearing: {@link #update()}
     * zeroes it once a flagship reaches its holding depth, and {@link MoveStyle#HUNT} handing it
     * straight back is what keeps the boss parked there.
     */
    public void trackAcross(Entity target) {
        double toTarget = orientation.across(target.centerX() - centerX(),
                target.centerY() - centerY());
        double jink = kind == null ? 0 : kind.weave();
        // The phase offset goes to the swerve and nowhere else. It exists to stop two ships tracing
        // one line, which is a statement about sideways motion; feeding it to the advance as well
        // was starting DIVE's wind-up up to a hundred ticks in, so most diving ships arrived
        // already committed and never read as diving at all.
        double across = isEnraged() ? trackSpeed * ENRAGE_TRACK_SCALE : trackSpeed;
        double step = move.acrossStep(toTarget, across, jink, age + weavePhase);
        double progress = orientation.depth(x(), y(), width(), height()) / orientation.arenaDepth();
        double advance = move.advance(orientation.along(velocityX(), velocityY()),
                descentSpeed, progress, age);
        setVelocity(orientation.vx(advance, step), orientation.vy(advance, step));
    }

    /** Whether this ship has come far enough into the arena to be worth driving or drawing. */
    public boolean hasEntered() {
        double extent = orientation.alongExtent(width(), height());
        return orientation.depth(x(), y(), width(), height()) > -extent / 2;
    }

    /**
     * Points this ship down the lane of the level it is entering.
     *
     * Stamped by {@link com.hashimjacobs.spacecase.engine.World#addEnemy} rather than taken as a
     * constructor argument: that catches escorts a boss calls in mid-fight for free, and leaves
     * every existing caller -- and every test that builds a ship directly -- on the top-down
     * default behaving exactly as before.
     */
    public void enter(Orientation orientation) {
        this.orientation = orientation;
        // Re-keyed off the position across the lane rather than off screen x plus y, now that the
        // lane is known. The two agree in a top-down level and disagree in a side-on one, which
        // meant the same authored ship jinked on a different beat depending on which way its level
        // happened to run -- invisible in play, and exactly the kind of thing that makes a wave
        // behave differently on level 9 for no reason anybody wrote down.
        this.weavePhase = Math.floorMod((int) orientation.across(x(), y()), MoveStyle.PERIOD_TICKS);
        double advance = isBoss() ? BOSS_DESCENT_SPEED : descentSpeed;
        setVelocity(orientation.vx(advance, 0), orientation.vy(advance, 0));
    }

    public Orientation orientation() {
        return orientation;
    }

    /**
     * Advances the timers the weapons do not drive. Called once per fixed step, by the world.
     *
     * Deliberately not folded into {@link #update()}. Every subclass that matters -- BossHead,
     * MechPart, PilotedMech, BurrowingWorm, VoidEntity -- overrides update() without calling super,
     * on purpose, because the station-keeping clamp there would wreck their movement. A timer put
     * in update() would therefore run for scouts and for nothing else, and every flagship on screen
     * would raise its bar at the first hit and never drop it again.
     *
     * This is the split PlayerShip already has, and World already calls tickTimers() beside
     * update() for players.
     */
    public void tickTimers() {
        if (hitBarTicks > 0) {
            hitBarTicks--;
        }
    }

    /** Counts down the weapon timer and reports whether the ship may fire this tick. */
    public boolean tickWeapon(int cooldownTicks) {
        if (fireCooldown > 0) {
            fireCooldown--;
            return false;
        }
        fireCooldown = cooldownTicks;
        return true;
    }

    /**
     * The rocket salvo's own timer, run alongside {@link #tickWeapon} rather than instead of it.
     *
     * A flagship has two weapons at once: the pattern its {@code BossPhase} describes, and this. A
     * phase could not express that, since only one phase is active at a time.
     */
    public boolean tickRocket(int cooldownTicks) {
        if (rocketCooldown > 0) {
            rocketCooldown--;
            return false;
        }
        rocketCooldown = cooldownTicks;
        return true;
    }

    /** How much tougher than authored this ship is; always 1 for an ordinary enemy. */
    public double scale() {
        return scale;
    }

    /**
     * This ship's share of the preset's gap between shots: its archetype's, times its wave row's.
     *
     * On the ship rather than read off {@link EnemyKind} at the call site, because two things now
     * decide it and only the ship knows both.
     */
    public double fireFactor() {
        return fireFactor;
    }

    /** How many shots this ship puts out at once. One for anything a wave did not ask otherwise. */
    public int shots() {
        return shots;
    }

    /**
     * Applies damage, unless a live part is shielding this ship.
     *
     * A hydra's torso takes nothing while a head still bites, which is what makes the heads the
     * fight rather than decoration. An ordinary ship has no parts, so the loop costs nothing.
     */
    public void takeDamage(int amount) {
        // Above the shield check, not below it. A shot a live head absorbed still landed on this
        // hull, and the full bar it raises says "and you are not moving it" -- which is the same
        // thing MechPart's guarded cockpit still flashing and still playing is for. Feedback that
        // vanishes reads as broken collision; feedback that will not budge reads as armour.
        showHitBar();
        for (EnemyShip part : parts) {
            if (part.isAlive()) {
                return;
            }
        }
        health -= amount;
        if (health <= 0) {
            kill();
        }
    }

    /** True for a head or segment of a larger flagship, false for the flagship itself. */
    public boolean isBossPart() {
        return false;
    }

    /** The parts shielding this ship, empty for everything but a multi-part flagship. */
    public List<EnemyShip> parts() {
        return parts;
    }

    /**
     * How many parts the flagship this belongs to fields, or one if this is not a part.
     *
     * Read by EnemyWeapons to work out how much slower one part should fire than a whole ship
     * would. Counts parts rather than {@code boss.heads()} because the two disagree: the rig
     * declares two heads and fields three targets, an arm, an arm and a cockpit.
     *
     * Dead parts stay in the list, so this does not climb as a fight is won -- the barrage thins
     * because there are fewer mouths left, not because the survivors speed up.
     */
    public int siblingParts() {
        return 1;
    }

    /**
     * Whether any piece that guards the rest of this ship is still alive.
     *
     * Only {@link MechPart} distinguishes a guard from a guarded piece; for everything else every
     * part guards, which is the rule {@link #takeDamage} already applies to the body.
     */
    boolean hasLivingGuard() {
        for (EnemyShip part : parts) {
            boolean guards = !(part instanceof MechPart pod) || pod.isGuard();
            if (guards && part.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /** Which pattern this ship is firing. Overridden by parts that fight on their own clock. */
    public BossPhase phase() {
        return boss.phaseFor(remainingHealthFraction());
    }

    /**
     * Volleys this flagship has fired. Drives the special's cadence and nothing else.
     *
     * On the ship rather than in {@code engine.EnemyWeapons} because a flagship outlives no tick but
     * the weapons class is static: there is nowhere else for a per-ship count to live, and a map
     * keyed by ship in the weapons class would be the same field with a leak attached.
     */
    private int volleys;

    /** Ticks the volley counter. Called once per volley, after the shot goes out. */
    public void countVolley() {
        volleys++;
    }

    /** How many primaries go out between specials. Three, then the special, as the pattern. */
    private static final int VOLLEYS_PER_SPECIAL = 4;

    /**
     * The pattern going out on this volley: the health phase, or the special every fourth time.
     *
     * Asked twice a tick -- once by the cooldown, once by the fire -- and it has to give the same
     * answer to both, which is why {@link #volleys} is advanced after the shot rather than before.
     * A phase that changed between the two would arm a special and then fire it on the primary's
     * clock.
     *
     * Parts sit this out. A hydra's heads firing a lance each on their own counters would be three
     * specials in the air with nobody having chosen that, and the torso is the thing with the
     * rhythm.
     */
    public BossPhase firingPhase() {
        if (isBossPart() || volleys % VOLLEYS_PER_SPECIAL != VOLLEYS_PER_SPECIAL - 1) {
            return phase();
        }
        return boss.special();
    }

    /**
     * Whether this flagship has been driven into its last third and speeds up for it.
     *
     * Read off the body's own health, so a multi-part flagship enrages when the torso is nearly
     * dead rather than when one head is.
     */
    public boolean isEnraged() {
        return isBoss() && boss.enrages() && remainingHealthFraction() <= ENRAGE_BELOW;
    }

    /** The share of its health at which a flagship that enrages does so. Its last phase. */
    private static final double ENRAGE_BELOW = 0.33;

    /** How much harder an enraged flagship strafes. Enough to close a gap, not enough to be unfair. */
    private static final double ENRAGE_TRACK_SCALE = 1.55;

    /** The frames this ship animates through: a part wears its own art, not the flagship's. */
    public BossArt bossArt() {
        return isBossPart() ? boss.headArt() : boss.art();
    }

    /**
     * Offsets the first shot and the first salvo.
     *
     * Three heads built in the same tick would otherwise fire in perfect unison, which reads as
     * one weapon rather than three. Same trick the EnemyKind constructor uses for a wave.
     */
    protected void stagger(int weaponTicks, int secondaryTicks) {
        fireCooldown += weaponTicks;
        rocketCooldown += secondaryTicks;
    }

    /** The archetype, or null for a boss. */
    public EnemyKind kind() {
        return kind;
    }

    /** Which flagship this is, or null for an ordinary enemy. */
    public Boss boss() {
        return boss;
    }

    /**
     * Remaining health as a 0..1 fraction, counting any parts, for the boss bar and the phase clock.
     *
     * Summing the parts does two jobs at once: the bar starts draining from the first shot even
     * though the torso is untouchable, and the torso's own pattern escalates as its heads die --
     * without a second state machine to track how many are left.
     */
    public double remainingHealthFraction() {
        int live = Math.max(0, health);
        int max = maxHealth;
        for (EnemyShip part : parts) {
            live += Math.max(0, part.health);
            max += part.maxHealth;
        }
        return live / (double) max;
    }

    /** Raises the floating bar without touching health, for a hit that armour ate. */
    protected void showHitBar() {
        hitBarTicks = HIT_BAR_TICKS;
    }

    /**
     * How solidly the floating health bar should be drawn, and zero when there is none to draw.
     *
     * An alpha rather than a tick count, for the reason PlayerShip.justHit() is a boolean rather
     * than a getter: the renderer wants to know how to draw the thing, not how its window is timed.
     * It also keeps the fade length private, so nothing outside can drift out of step with it.
     */
    public double hitBarAlpha() {
        return Math.min(1, hitBarTicks / (double) HIT_BAR_FADE_TICKS);
    }

    /**
     * This box's own hull as a 0..1 fraction, ignoring any parts.
     *
     * Deliberately not {@link #remainingHealthFraction()}. That one answers "how far through this
     * fight are we", which is what the HUD bar and the phase clock want; a bar floating over one
     * box has to answer "how much is left in the thing I am pointing at". For an ordinary enemy the
     * two are identical, because parts is empty. For a hydra torso whose heads are dead they differ
     * by the heads' whole share, and a floating bar would appear on an untouched hull at 0.55 --
     * which reads as a bug rather than as a boss.
     */
    public double hullFraction() {
        return Math.max(0, health) / (double) maxHealth;
    }

    public int scoreValue() {
        return scoreValue;
    }

    public boolean isBoss() {
        boolean flagship = boss != null;
        return flagship;
    }

    /**
     * Enemy archetypes, ordered roughly by threat. Flagships are {@link Boss}, not one of these.
     *
     * Stats only: the hull comes from the level being fought, via {@code Level.enemySprite}.
     *
     * The health figures are picked against shot counts rather than as a multiplier. The one that
     * decides them is the scout's: at 24 it is two shots from a maxed gun rather than one, which is
     * the difference between a wave you fly through and a wave you fight. A fighter is three maxed
     * shots and a cruiser seven. Note these multiply with {@code prefs.Difficulty.enemyScale} and
     * again with a wave row's own factor, so the ceiling is a long way above what is written here.
     */
    public enum EnemyKind {

        SCOUT(24, 2.4, 2.5, 15, 1.0, 3.0, 0),
        FIGHTER(60, 1.2, 1.35, 25, 0.72, 1.3, 6),
        CRUISER(130, 0.62, 0.7, 45, 0.5, 0, 18);

        private final int health;
        private final double descentSpeed;
        private final double trackSpeed;
        private final int scoreValue;
        private final double fireFactor;
        private final double weave;
        private final int dropChancePercent;

        EnemyKind(int health, double descentSpeed, double trackSpeed, int scoreValue,
                  double fireFactor, double weave, int dropChancePercent) {
            this.health = health;
            this.descentSpeed = descentSpeed;
            this.trackSpeed = trackSpeed;
            this.scoreValue = scoreValue;
            this.fireFactor = fireFactor;
            this.weave = weave;
            this.dropChancePercent = dropChancePercent;
        }

        /**
         * Chance a shot kill of this hull leaves a pickup, in percent.
         *
         * Here rather than in {@code engine.CollisionSystem} because it is a property of the
         * archetype, next to the hull and the bounty it is priced against.
         *
         * It replaced one flat 28% across all three, which was tuned when a wave was five ships. A
         * wave is three groups of six to ten now, so the same figure paid out about seven pickups a
         * wave and the arena rained. <strong>Zero on the scout is the load-bearing number</strong>
         * -- scouts are most of what is on the field, and they are chaff rather than something you
         * choose to fight. Loot comes from the hulls worth killing for it.
         */
        public int dropChancePercent() {
            return dropChancePercent;
        }

        /**
         * Multiplier on the difficulty's gap between shots. Below one means this hull fires faster.
         *
         * The three archetypes used to differ only in how much hull and how much speed they carried,
         * which made a cruiser a scout that took longer to kill. Rate of fire is what gives each one
         * a different answer: outrun a scout, trade with a fighter, break line of sight on a cruiser.
         *
         * SCOUT is exactly one deliberately -- it is the reference the difficulty preset names, and
         * the other two are read against it.
         */
        public double fireFactor() {
            return fireFactor;
        }

        /**
         * Peak sideways swerve, in pixels a tick, on top of tracking the player.
         *
         * Larger than {@link #trackSpeed} on the scout on purpose: at that amplitude the sine
         * overpowers the tracking term for part of every cycle, so the ship genuinely breaks away
         * and comes back rather than closing in a straight line that wobbles. That is the whole
         * difference between "harder to hit" and "unpredictable".
         *
         * Zero on the cruiser: a gunship that jinks is not a gunship, and it is the one archetype
         * whose threat is meant to be readable so you can decide to leave.
         */
        public double weave() {
            return weave;
        }

        public int health() {
            return health;
        }

        public double descentSpeed() {
            return descentSpeed;
        }

        public double trackSpeed() {
            return trackSpeed;
        }

        public int scoreValue() {
            return scoreValue;
        }
    }
}
