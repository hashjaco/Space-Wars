package com.hashimjacobs.spacecase.onfoot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.mode.Level;

/**
 * An on-foot stage: land, fight through three locked arenas and the boss, take the prize, leave.
 *
 * Plain Java and deterministic -- one seeded {@link Random}, fixed steps, no clocks -- so it tests
 * without a toolkit and could run in lockstep later. For now it runs locally between the victory lap
 * and the debrief, like the garage, and touches neither score nor replay.
 *
 * The layout, boss and look come from a {@link Mission}, one per galaxy.
 */
public final class OnFootStage {

    public enum Beat { LANDING, EXIT, FIGHT, KEY, PICKUP, LIFTOFF, DONE }

    /** The floor line, in canvas pixels. Everything stands on it. */
    public static final double FLOOR = GameConfig.HEIGHT * 0.83;
    public static final double LENGTH = 4200;
    static final double SHIP_X = 260;
    static final int LANDING_TICKS = 130;
    static final int EXIT_TICKS = 50;
    static final int LIFTOFF_TICKS = 150;
    static final int RETRY_TICKS = 120;
    /** Attackers allowed at once, per pilot on their feet: a crowd that scales with the party. */
    static final int ATTACKERS_PER_PILOT = 2;

    /** Which stage, if any, this level's clear sends the pilots down to. */
    public static Mission missionAfter(Level level) {
        return Mission.after(level);
    }

    /** A locked arena: walls at left and right, these enemies, cleared when they are all down. */
    record Segment(double left, double right, int thralls, int riveteers, int brutes, boolean boss) {
    }

    /**
     * A patch of floor a boss signature leaves: slag that burns while it lasts, a spike mark that
     * erupts once, or the lit line an Arc Tyrant is about to dash down. {@code x} is the middle.
     * {@code arm} counts down
     * while it only warns; {@code live} counts down while it bites.
     */
    public record Hazard(int kind, double x, double width, int arm, int live, int maxLive) {
        public static final int SLAG = 0, SPIKE = 1, LINE = 2;
        public boolean armed() { return arm == 0; }
    }

    /** One enemy waiting to drop in, and from which side. */
    private record Spawn(Fighter.Kind kind, int side) {
    }

    public record Beam(Fighter owner, double x, double y, int dir, double length, double thickness,
                       int life, int maxLife, int damage, int every, boolean needle) {
        double left() { return dir > 0 ? x : x - length; }
        double right() { return dir > 0 ? x + length : x; }
    }

    /** A shock rolling along the floor from the Foreman's pound. Jump it. */
    public record Shock(double x, int dir, int life) {
    }

    /** Something worth a sound this step; the loop plays them, the stage stays silent and testable. */
    public enum Cue { HIT, HEAVY, BLOCK, BEAM, POUND, ROAR, KEY, LAND }

    public record Particle(double x, double y, double vx, double vy, int life, int maxLife, int kind) {
        public static final int SPARK = 0, DUST = 1, DEBRIS = 2, KI = 3;
    }

    private final Physics physics;
    private final Mission mission;
    private final List<Segment> SEGMENTS;
    private final Random random;
    private final List<Fighter> pilots = new ArrayList<>();
    private final List<Fighter> enemies = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();
    private final List<Shock> shocks = new ArrayList<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Spawn> pendingSpawns = new ArrayList<>();
    private final List<Cue> cues = new ArrayList<>();

    private Beat beat = Beat.LANDING;
    private int beatTicks;
    private int tick;
    private int freeze;
    private double shake;
    private double zoomKick;
    private double camera;
    private int segment = -1;
    private boolean locked;
    private int retry;
    private int spawnClock;
    private double keyX = -1, keyY;
    private double shipX = SHIP_X, shipY = -200;

    public OnFootStage(long seed, int players, Physics physics) {
        this(seed, players, physics, Mission.UNDERCITY);
    }

    public OnFootStage(long seed, int players, Physics physics, Mission mission) {
        this.physics = physics;
        this.mission = mission;
        this.SEGMENTS = mission.segments;
        this.random = new Random(seed);
        for (int p = 1; p <= players; p++) {
            Fighter pilot = new Fighter(Fighter.Kind.PILOT, p, SHIP_X + 40 + 40 * p);
            pilot.health = 0;     // still in the cockpit until EXIT
            pilot.state = Fighter.State.DEAD;
            pilots.add(pilot);
        }
    }

    // ---- read side

    public Beat beat() { return beat; }
    public int beatTicks() { return beatTicks; }
    public int tick() { return tick; }
    public boolean done() { return beat == Beat.DONE; }
    public List<Fighter> pilots() { return pilots; }
    public List<Fighter> enemies() { return enemies; }
    public List<Beam> beams() { return beams; }
    public List<Shock> shocks() { return shocks; }
    public List<Hazard> hazards() { return hazards; }
    public Mission mission() { return mission; }
    public List<Particle> particles() { return particles; }
    public double camera() { return camera; }
    /** This step's sounds. Cleared at the start of the next step. */
    public List<Cue> cues() { return cues; }
    public double shake() { return shake; }
    public double zoomKick() { return zoomKick; }
    public boolean locked() { return locked; }
    public double wallLeft() { return locked ? SEGMENTS.get(segment).left() : 0; }
    public double wallRight() { return locked ? SEGMENTS.get(segment).right() : LENGTH; }
    public boolean hasKey() { return keyX >= 0; }
    public double keyX() { return keyX; }
    public double keyY() { return keyY; }
    public double shipX() { return shipX; }
    public double shipY() { return shipY; }
    public int retrying() { return retry; }
    public int segment() { return segment; }
    public boolean frozen() { return freeze > 0; }
    public Fighter boss() {
        for (Fighter e : enemies) {
            if (e.kind.boss()) {
                return e;
            }
        }
        return null;
    }
    /** A "go" arrow, after an arena opens and before the next one locks. */
    public boolean goPrompt() {
        return beat == Beat.FIGHT && !locked && segment < SEGMENTS.size() - 1;
    }

    // ---- simulation

    /** One step. {@code input} holds a pilot's buttons by seat, one entry per pilot. */
    public void step(List<Buttons> input) {
        cues.clear();
        tick++;
        beatTicks++;
        shake *= 0.86;
        zoomKick *= 0.85;
        if (freeze > 0) {
            // Hit-stop: the world holds still, which is where most of a punch's weight comes from.
            // The pilots' thumbs do not: a press made during it is kept, so the jump over a pound's
            // shockwave -- pressed as the hit lands, which is when hit-stop runs -- is not lost.
            for (Fighter pilot : pilots) {
                Buttons b = pilot.player - 1 < input.size() ? input.get(pilot.player - 1) : Buttons.NONE;
                pilot.latch(b, physics);
            }
            freeze--;
            return;
        }
        switch (beat) {
            case LANDING -> {
                double t = Math.min(1, beatTicks / (double) LANDING_TICKS);
                double ease = 1 - Math.pow(1 - t, 3);
                shipY = -220 + (FLOOR - 40 + 220) * ease;
                if (beatTicks == LANDING_TICKS) {
                    shake = 10;
                    cues.add(Cue.LAND);
                    for (int i = 0; i < 26; i++) {
                        dust(SHIP_X + (random.nextDouble() - 0.5) * 220, FLOOR, 1.8);
                    }
                    next(Beat.EXIT);
                }
            }
            case EXIT -> {
                if (beatTicks == 12) {
                    for (Fighter pilot : pilots) {
                        pilot.health = pilot.kind.health;
                        pilot.x = SHIP_X;
                        pilot.y = FLOOR - 70;
                        pilot.vx = 2.6 + pilot.player * 1.4;
                        pilot.vy = -9;
                        pilot.grounded = false;
                        pilot.enter(Fighter.State.FREE);
                    }
                }
                simulate(input);
                if (beatTicks >= EXIT_TICKS) {
                    next(Beat.FIGHT);
                }
            }
            case FIGHT -> {
                simulate(input);
                arenas();
            }
            case KEY -> {
                simulate(input);
                for (Fighter pilot : pilots) {
                    if (pilot.alive() && Math.abs(pilot.x - keyX) < 50 && pilot.grounded) {
                        keyX = -1;
                        shake = 6;
                        cues.add(Cue.KEY);
                        shipX = pilot.x - 40;
                        shipY = -220;
                        next(Beat.PICKUP);
                    }
                }
                // Gently falls to the floor where the boss dropped it.
                keyY = Math.min(FLOOR - 30, keyY + 3);
            }
            case PICKUP -> {
                simulate(input);
                double t = Math.min(1, beatTicks / (double) LANDING_TICKS);
                shipY = -220 + (FLOOR - 40 + 220) * (1 - Math.pow(1 - t, 3));
                if (beatTicks == LANDING_TICKS) {
                    shake = 8;
                    next(Beat.LIFTOFF);
                }
            }
            case LIFTOFF -> {
                for (Fighter pilot : pilots) {
                    pilot.enter(Fighter.State.DEAD);    // aboard
                }
                shipY -= Math.max(0.5, (beatTicks - 30) * 0.35);
                if (beatTicks % 3 == 0) {
                    dust(shipX + (random.nextDouble() - 0.5) * 120, FLOOR, 1.2);
                }
                if (beatTicks >= LIFTOFF_TICKS) {
                    next(Beat.DONE);
                }
            }
            case DONE -> {
            }
        }
        followCamera();
        moveParticles();
    }

    private void next(Beat b) {
        beat = b;
        beatTicks = 0;
    }

    private void simulate(List<Buttons> input) {
        for (Fighter pilot : pilots) {
            if (!pilot.alive()) {
                continue;
            }
            Buttons b = pilot.player - 1 < input.size() ? input.get(pilot.player - 1) : Buttons.NONE;
            pilot.think(b, physics);
        }
        if (beat == Beat.FIGHT) {
            brains();
        }
        for (Fighter f : all()) {
            if (f.state == Fighter.State.DEAD && f.grounded) {
                f.vx *= physics.friction();
                f.x += f.vx;
                f.stateTicks++;     // the corpse clock, which is what sweeps it
                continue;
            }
            boolean wasLaunched = f.state == Fighter.State.LAUNCHED;
            if (f.integrate(physics, wallLeft(), wallRight())) {
                for (int i = 0; i < (wasLaunched ? 10 : 4); i++) {
                    dust(f.x + (random.nextDouble() - 0.5) * f.kind.width, FLOOR, wasLaunched ? 1.6 : 0.8);
                }
                if (wasLaunched && f.kind != Fighter.Kind.PILOT) {
                    shake = Math.max(shake, 4);
                }
            }
            f.tickState();
            if (f.state == Fighter.State.ATTACK && f.move != null) {
                fireMoveEffects(f);
            }
        }
        melee();
        beamsTick();
        shocksTick();
        hazardsTick();
        sweepDead();
        retryIfWiped();
    }

    private List<Fighter> all() {
        List<Fighter> everyone = new ArrayList<>(pilots.size() + enemies.size());
        everyone.addAll(pilots);
        everyone.addAll(enemies);
        return everyone;
    }

    /** The moves that make something rather than hitting with a hitbox: beams and the pound. */
    private void fireMoveEffects(Fighter f) {
        int t = f.stateTicks;
        Move m = f.move;
        double chest = f.y - f.kind.height * 0.6;
        double muzzle = f.x + f.facing * f.kind.width * 0.6;
        if (m == Move.NEEDLE_RAY && t == m.startup) {
            beams.add(new Beam(f, muzzle, chest, f.facing, 1100, 9, m.active, m.active, m.damage, 999, true));
            cues.add(Cue.BEAM);
            kick(3, 2);
        } else if (m == Move.SOLAR_LANCE && t == m.startup) {
            double p = f.lancePower;
            int life = (int) (26 + 34 * p);
            beams.add(new Beam(f, muzzle, chest, f.facing, 620 + 520 * p, 26 + 62 * p, life, life,
                    (int) Math.round(3 + 5 * p), 6, false));
            cues.add(Cue.BEAM);
            f.vx = -f.facing * (2 + 4 * p);    // recoil
            kick(6 + 8 * p, 3 + 4 * p);
        } else if (m == Move.MOLTEN_BEAM && t == m.startup) {
            int life = f.rage() ? 70 : 54;
            // Half the floor, not all of it, and slow to re-hit: stepping or jumping out of it works.
            beams.add(new Beam(f, muzzle, f.y - f.kind.height * 0.55, f.facing, 560, 54, life, life, 5, 20, false));
            cues.add(Cue.BEAM);
            kick(9, 4);
        } else if ((m == Move.SOLAR_LANCE || m == Move.MOLTEN_BEAM) && t > m.startup && beamOf(f) != null) {
            // Rooted, holding the pose, for as long as the beam lives; recovery starts when it ends.
            f.stateTicks = m.startup + 1;
        } else if (m == Move.POUND && t == m.startup) {
            shocks.add(new Shock(f.x, 1, f.rage() ? 110 : 80));
            shocks.add(new Shock(f.x, -1, f.rage() ? 110 : 80));
            cues.add(Cue.POUND);
            kick(12, 5);
            for (int i = 0; i < 18; i++) {
                debris(f.x + (random.nextDouble() - 0.5) * 160, FLOOR);
            }
        } else if (m == Move.SLAG_SLAM && t == m.startup) {
            // Where the hammer lands, a splash further out, and one behind: the floor around it burns.
            int life = f.rage() ? 240 : 180;
            for (double off : new double[] {110, 330, -190}) {
                hazards.add(new Hazard(Hazard.SLAG, f.x + f.facing * off, 120, 18, life, life));
            }
            cues.add(Cue.POUND);
            kick(12, 5);
            for (int i = 0; i < 18; i++) {
                debris(f.x + f.facing * 110 + (random.nextDouble() - 0.5) * 160, FLOOR);
            }
        } else if (m == Move.ICE_SPIKES && t == m.startup) {
            // A mark under every pilot standing, erupting a beat later. Stand still and it lands.
            for (Fighter p : pilots) {
                if (p.alive()) {
                    hazards.add(new Hazard(Hazard.SPIKE, p.x, 96, f.rage() ? 34 : 45, 12, 12));
                }
            }
            cues.add(Cue.ROAR);
        } else if (m == Move.ARC_DASH && t == 1) {
            double end = f.facing > 0 ? wallRight() : wallLeft();
            hazards.add(new Hazard(Hazard.LINE, (f.x + end) / 2, Math.abs(end - f.x), m.startup - 1,
                    m.active, m.active));
        } else if (m == Move.ARC_DASH && f.hitting()) {
            f.vx = f.facing * (f.rage() ? 17 : 15);
        } else if (m == Move.GRAVITY_WELL && f.hitting()) {
            // Slower than a walk, so running against it holds ground and standing still does not.
            for (Fighter p : pilots) {
                if (p.alive() && Math.abs(p.x - f.x) > 60) {
                    p.x += Math.signum(f.x - p.x) * (f.rage() ? 2.8 : 2.2);
                }
            }
            if (t == m.startup + m.active - 1) {
                shocks.add(new Shock(f.x, 1, 110));
                shocks.add(new Shock(f.x, -1, 110));
                cues.add(Cue.POUND);
                kick(12, 5);
            }
        } else if (m == Move.RIVET && t == m.startup) {
            beams.add(new Beam(f, muzzle, chest, f.facing, 600, 7, 10, 10, m.damage, 999, false));
            cues.add(Cue.BEAM);
        } else if (m == Move.RAM && f.hitting()) {
            f.vx = f.facing * 9;    // the charge itself: the whole active window at a run
        } else if (m == Move.LUNGE && f.hitting()) {
            f.vx = f.facing * 7;    // driven through the whole active window, or friction eats it
        } else if (m == Move.SLAM && t == m.startup - 6) {
            f.vx = f.facing * 5;
        }
    }

    private Beam beamOf(Fighter f) {
        for (Beam b : beams) {
            if (b.owner() == f) {
                return b;
            }
        }
        return null;
    }

    /** Every active hitbox against every body on the other side, once per move per target. */
    private void melee() {
        for (Fighter a : all()) {
            if (!a.hitting() || a.move.reach <= 0) {
                continue;
            }
            Move m = a.move;
            double front = a.facing > 0 ? a.right() - 10 : a.left() + 10;
            double hx0 = a.facing > 0 ? front : front - m.reach;
            double hx1 = a.facing > 0 ? front + m.reach : front;
            double hy0 = a.top() + a.kind.height * m.top;
            double hy1 = a.top() + a.kind.height * m.bottom;
            for (Fighter t : opponents(a)) {
                if (!t.hittable() || a.struck.contains(t)) {
                    continue;
                }
                if (t.right() < hx0 || t.left() > hx1 || t.y < hy0 || t.top() > hy1) {
                    continue;
                }
                a.struck.add(t);
                land(a, t, m, -1, a.facing, m.knockX, m.knockY, (hx0 + hx1) / 2 + a.facing * m.reach * 0.25,
                        Math.max(hy0, t.top() + t.kind.height * 0.3));
            }
        }
    }

    private List<Fighter> opponents(Fighter a) {
        return a.kind == Fighter.Kind.PILOT ? enemies : pilots;
    }

    private void land(Fighter a, Fighter t, Move m, int damage, int dir, double kx, double ky,
                      double atX, double atY) {
        boolean blocked = t.state == Fighter.State.GUARD && t.facing == -dir && t.grounded;
        t.takeHit(m, damage, dir, kx, ky, physics);
        if (a.kind == Fighter.Kind.PILOT && !blocked) {
            a.ki = Math.min(Fighter.MAX_KI, a.ki + 3);
        }
        freeze = Math.max(freeze, blocked ? 2 : m.hitstop);
        Cue cue = blocked ? Cue.BLOCK : m.heavy() ? Cue.HEAVY : Cue.HIT;
        if (!cues.contains(cue)) {
            cues.add(cue);
        }
        int sparks = blocked ? 4 : m.heavy() ? 16 : 8;
        for (int i = 0; i < sparks; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double sp = 2 + random.nextDouble() * (m.heavy() ? 7 : 4);
            particles.add(new Particle(atX, atY, dir * Math.abs(StrictMath.cos(ang)) * sp,
                    StrictMath.sin(ang) * sp - 1.5, 14, 14, Particle.SPARK));
        }
        if (m.heavy() && !blocked) {
            kick(7, 5);
        } else {
            shake = Math.max(shake, blocked ? 1.5 : 2.5);
        }
    }

    private void kick(double shakeBy, double zoomBy) {
        shake = Math.max(shake, shakeBy);
        zoomKick = Math.max(zoomKick, zoomBy);
    }

    private void beamsTick() {
        for (Iterator<Beam> it = beams.iterator(); it.hasNext(); ) {
            Beam b = it.next();
            int age = b.maxLife() - b.life();
            if (b.needle() ? age == 0 : age % b.every() == 0) {
                Move m = b.owner().move == null ? Move.NEEDLE_RAY : b.owner().move;
                for (Fighter t : opponents(b.owner())) {
                    if (!t.hittable()) {
                        continue;
                    }
                    double half = b.thickness() / 2 + 8;
                    double mid = t.y - t.kind.height / 2;
                    if (t.right() < b.left() || t.left() > b.right()
                            || Math.abs(mid - b.y()) > half + t.kind.height / 2) {
                        continue;
                    }
                    double push = b.needle() ? 4 : 3 + b.thickness() / 30;
                    land(b.owner(), t, m, b.damage(), b.dir(), push, b.needle() ? -2 : -1.5,
                            t.x - b.dir() * t.kind.width / 2, b.y());
                }
            }
            Beam aged = new Beam(b.owner(), b.owner().x + b.dir() * b.owner().kind.width * 0.6, b.y(), b.dir(),
                    b.length(), b.thickness(), b.life() - 1, b.maxLife(), b.damage(), b.every(), b.needle());
            it.remove();
            if (aged.life() > 0 && b.owner().state == Fighter.State.ATTACK) {
                beamsReplace.add(aged);
            }
        }
        beams.addAll(beamsReplace);
        beamsReplace.clear();
    }

    private final List<Beam> beamsReplace = new ArrayList<>();

    private void shocksTick() {
        List<Shock> moved = new ArrayList<>();
        for (Shock s : shocks) {
            double x = s.x() + s.dir() * 8;
            for (Fighter p : pilots) {
                if (p.hittable() && p.grounded && Math.abs(p.x - x) < 30) {
                    land(boss() != null ? boss() : p, p, Move.POUND, 12, s.dir(), 7, -8, x, FLOOR - 20);
                }
            }
            if (tick % 2 == 0) {
                debris(x, FLOOR);
            }
            if (s.life() > 1 && x > wallLeft() && x < wallRight()) {
                moved.add(new Shock(x, s.dir(), s.life() - 1));
            }
        }
        shocks.clear();
        shocks.addAll(moved);
    }

    private void hazardsTick() {
        List<Hazard> kept = new ArrayList<>(hazards.size());
        Fighter owner = boss();
        for (Hazard h : hazards) {
            if (!h.armed()) {
                kept.add(new Hazard(h.kind(), h.x(), h.width(), h.arm() - 1, h.live(), h.maxLive()));
                continue;
            }
            boolean bites = switch (h.kind()) {
                case Hazard.SLAG -> h.live() % 15 == 0;
                case Hazard.SPIKE -> h.live() == h.maxLive();
                default -> false;
            };
            if (bites) {
                for (Fighter p : pilots) {
                    // On the floor only: a jump clears slag, and a spike that erupts under you.
                    boolean over = Math.abs(p.x - h.x()) < h.width() / 2;
                    if (p.hittable() && over && FLOOR - p.y < 40) {
                        boolean spike = h.kind() == Hazard.SPIKE;
                        land(owner != null ? owner : p, p, spike ? Move.ICE_SPIKES : Move.RIVET,
                                spike ? Move.ICE_SPIKES.damage : 4, p.facing > 0 ? -1 : 1,
                                spike ? 3 : 2, spike ? -12 : -2, p.x, FLOOR - 20);
                    }
                }
                if (h.kind() == Hazard.SPIKE) {
                    cues.add(Cue.HEAVY);
                    for (int i = 0; i < 10; i++) {
                        debris(h.x() + (random.nextDouble() - 0.5) * h.width(), FLOOR);
                    }
                }
            }
            if (h.live() > 1) {
                kept.add(new Hazard(h.kind(), h.x(), h.width(), 0, h.live() - 1, h.maxLive()));
            }
        }
        hazards.clear();
        hazards.addAll(kept);
    }

    /** The crowd. A couple of attackers per pilot hold a token; the rest circle, which is what keeps a crowd fair. */
    private void brains() {
        int attacking = 0;
        for (Fighter e : enemies) {
            if (e.token && (!e.alive() || e.state == Fighter.State.DOWN)) {
                e.token = false;
            }
            attacking += e.token ? 1 : 0;
        }
        int flankers = 0;
        for (Fighter e : enemies) {
            if (!e.actionable()) {
                continue;
            }
            Fighter target = nearestPilot(e.x);
            if (target == null) {
                e.vx *= 0.8;
                continue;
            }
            double gap = target.x - e.x;
            double dist = Math.abs(gap);
            int toward = gap >= 0 ? 1 : -1;
            e.facing = toward;
            e.cooldown--;
            if (e.kind.boss()) {
                boss(e, dist, toward);
                continue;
            }
            // One budget for the whole crowd: riveteers and brutes wait their turn like the thralls.
            if (!e.token && attacking < ATTACKERS_PER_PILOT * livePilots() && e.cooldown <= 0) {
                e.token = true;
                attacking++;
            }
            if (e.kind == Fighter.Kind.RIVETEER) {
                if (riveteer(e, target, dist, toward)) {
                    e.token = false;
                    attacking--;
                }
                continue;
            }
            if (e.kind == Fighter.Kind.BRUTE) {
                if (brute(e, dist, toward)) {
                    e.token = false;
                    attacking--;
                }
                continue;
            }
            double want = e.token ? 54 + e.kind.width / 2 : 200 + (enemies.indexOf(e) % 5) * 24;
            // Every second attacker works round behind the pilot: a crowd that only comes from the
            // front is a crowd a mashed jab holds off forever.
            boolean flank = e.token && flankers++ % 2 == 1;
            int side = flank ? -target.facing : -toward;
            double goal = target.x + side * want;
            double speed = physics.walkSpeed() * e.kind.pace;
            if (Math.abs(goal - e.x) > 12) {
                e.vx = Fighter.approach(e.vx, Math.signum(goal - e.x) * speed * (flank ? 1.25 : 1), 0.5);
            } else {
                e.vx *= 0.8;
            }
            if (e.token && e.cooldown <= 0 && dist < want + 24 && target.alive()) {
                e.start(dist > 110 || random.nextInt(4) == 0 ? Move.LUNGE : Move.CLAW);
                e.cooldown = 34 + random.nextInt(44);
                if (random.nextInt(3) == 0) {
                    e.token = false;   // hand the turn to someone else
                    attacking--;
                }
            }
        }
    }

    /**
     * Every boss walks in and fights the Foreman's way up close. The Foreman keeps its beam and
     * pound at range; the others use their one signature there, and sometimes up close too.
     */
    private void boss(Fighter e, double dist, int toward) {
        Move signature = switch (e.kind) {
            case WARDEN -> Move.SLAG_SLAM;
            case MATRIARCH -> Move.ICE_SPIKES;
            case TYRANT -> Move.ARC_DASH;
            case REVENANT -> Move.GRAVITY_WELL;
            default -> null;
        };
        if (signature == null) {
            foreman(e, dist, toward);
            return;
        }
        boolean rage = e.rage();
        double speed = physics.walkSpeed() * e.kind.pace * (rage ? 1.35 : 1);
        if (dist > 150) {
            e.vx = Fighter.approach(e.vx, toward * speed, 0.3);
        } else {
            e.vx *= 0.8;
        }
        if (e.cooldown > 0) {
            return;
        }
        int roll = random.nextInt(10);
        if (dist < 170 && roll < 6) {
            e.start(roll < 4 ? Move.PISTON : Move.SLAM);
            e.cooldown = (rage ? 40 : 70) + random.nextInt(40);
        } else {
            e.start(signature);
            // A signature gets its room to be read and answered before the next one.
            e.cooldown = (rage ? 70 : 110) + random.nextInt(40);
        }
    }

    private void foreman(Fighter e, double dist, int toward) {
        boolean rage = e.rage();
        double speed = physics.walkSpeed() * e.kind.pace * (rage ? 1.35 : 1);
        if (dist > 150) {
            e.vx = Fighter.approach(e.vx, toward * speed, 0.3);
        } else {
            e.vx *= 0.8;
        }
        if (e.cooldown > 0) {
            return;
        }
        int roll = random.nextInt(10);
        if (dist < 170) {
            e.start(roll < 6 ? Move.PISTON : Move.SLAM);
        } else if (dist > 380 || roll < 3) {
            e.start(Move.MOLTEN_BEAM);
        } else {
            e.start(Move.POUND);
        }
        e.cooldown = (rage ? 40 : 70) + random.nextInt(40);
    }

    /** Hangs back at rifle range, steps away from a pilot who closes in, and fires when lined up. */
    private boolean riveteer(Fighter e, Fighter target, double dist, int toward) {
        double speed = physics.walkSpeed() * e.kind.pace;
        double goal = dist < RIVET_NEAR ? -toward : dist > RIVET_FAR ? toward : 0;
        e.vx = goal == 0 ? e.vx * 0.8 : Fighter.approach(e.vx, goal * speed, 0.4);
        if (e.token && e.cooldown <= 0 && dist < RIVET_FAR + 140 && target.alive()) {
            e.start(Move.RIVET);
            e.cooldown = 90 + random.nextInt(60);
            return true;
        }
        return false;
    }

    /** Walks in. Close, it hooks; at range it lowers its shoulder and charges. */
    /** @return whether it attacked, which spends its turn */
    private boolean brute(Fighter e, double dist, int toward) {
        double speed = physics.walkSpeed() * e.kind.pace;
        if (dist > 110) {
            e.vx = Fighter.approach(e.vx, toward * speed, 0.3);
        } else {
            e.vx *= 0.8;
        }
        if (e.cooldown > 0 || !e.token) {
            return false;
        }
        if (dist < 130) {
            e.start(Move.SMASH);
            e.cooldown = 50 + random.nextInt(40);
            return true;
        } else if (dist < 440 && random.nextInt(3) == 0) {
            e.start(Move.RAM);
            e.cooldown = 80 + random.nextInt(50);
            return true;
        }
        return false;
    }

    /** The distance band a Riveteer keeps from its pilot. */
    static final double RIVET_NEAR = 300, RIVET_FAR = 420;

    /** Enemies on the floor at once, past the pilots: the rest wait on the catwalks. */
    static final int MAX_ON_FLOOR = 3;

    int liveEnemies() {
        int n = 0;
        for (Fighter e : enemies) {
            n += e.alive() ? 1 : 0;
        }
        return n;
    }

    private int livePilots() {
        int n = 0;
        for (Fighter p : pilots) {
            n += p.alive() ? 1 : 0;
        }
        return n;
    }

    private Fighter nearestPilot(double x) {
        Fighter best = null;
        for (Fighter p : pilots) {
            if (p.alive() && (best == null || Math.abs(p.x - x) < Math.abs(best.x - x))) {
                best = p;
            }
        }
        return best;
    }

    private void sweepDead() {
        for (Iterator<Fighter> it = enemies.iterator(); it.hasNext(); ) {
            Fighter e = it.next();
            if (e.state == Fighter.State.DEAD && e.stateTicks > 70) {
                if (e.kind.boss()) {
                    hazards.clear();
                    for (Fighter rest : enemies) {
                        if (rest.alive()) {
                            rest.health = 0;    // the crowd breaks when its boss falls
                            rest.enter(Fighter.State.DEAD);
                        }
                    }
                    keyX = e.x;
                    keyY = e.y - e.kind.height * 0.6;
                    kick(14, 6);
                    for (int i = 0; i < 40; i++) {
                        debris(e.x + (random.nextDouble() - 0.5) * 120, e.y - random.nextDouble() * 160);
                    }
                    next(Beat.KEY);
                }
                it.remove();
            }
        }
    }

    /** Locks the next arena once the pilots walk into it, feeds it enemies, and opens it when they are down. */
    private void arenas() {
        if (!locked) {
            int nextSegment = segment + 1;
            if (nextSegment >= SEGMENTS.size()) {
                return;
            }
            Segment s = SEGMENTS.get(nextSegment);
            double lead = 0;
            for (Fighter p : pilots) {
                lead = Math.max(lead, p.alive() ? p.x : 0);
            }
            if (lead > s.left() + 380) {
                segment = nextSegment;
                locked = true;
                spawnClock = 0;
                pendingSpawns.clear();
                for (int i = 0; i < s.thralls(); i++) {
                    pendingSpawns.add(new Spawn(Fighter.Kind.THRALL, i % 3 == 2 ? -1 : 1));
                }
                // The specialists come mid-wave, so the crowd they hide behind is already there.
                int at = Math.min(2, pendingSpawns.size());
                for (int i = 0; i < s.riveteers(); i++) {
                    pendingSpawns.add(Math.min(at + i, pendingSpawns.size()), new Spawn(Fighter.Kind.RIVETEER, i % 2 == 0 ? 1 : -1));
                }
                for (int i = 0; i < s.brutes(); i++) {
                    pendingSpawns.add(new Spawn(Fighter.Kind.BRUTE, 1));
                }
                if (s.boss()) {
                    Fighter foreman = spawn(mission.boss, s.right() - 160);
                    // Tougher with company: a second pilot roughly halves the fight otherwise.
                    foreman.health = foreman.kind.health * (0.65 + 0.35 * (pilots.size() - 1));
                    enemies.add(foreman);
                    kick(10, 0);
                    cues.add(Cue.ROAR);
                }
                for (Fighter p : pilots) {
                    // Anyone left outside the walls is pulled in rather than trapped out.
                    p.x = Math.max(p.x, s.left() + 60);
                }
            }
            return;
        }
        Segment s = SEGMENTS.get(segment);
        spawnClock++;
        // ponytail: a fixed cap on the floor at once; per mission if later galaxies feel too soft.
        if (!pendingSpawns.isEmpty() && spawnClock % 50 == 1 && liveEnemies() < MAX_ON_FLOOR + livePilots()) {
            Spawn next = pendingSpawns.remove(0);
            int side = next.side();
            double x = side > 0 ? Math.min(s.right() - 40, camera + GameConfig.WIDTH + 40)
                    : Math.max(s.left() + 40, camera - 40);
            Fighter e = spawn(next.kind(), x);
            e.vy = -6;      // they drop in from the catwalks
            e.y = FLOOR - 160;
            e.grounded = false;
            enemies.add(e);
        }
        boolean clear = pendingSpawns.isEmpty();
        for (Fighter e : enemies) {
            clear &= !e.alive() && !e.kind.boss();
        }
        if (clear && !s.boss()) {
            locked = false;
            for (Fighter p : pilots) {
                if (!p.alive()) {
                    // A downed partner gets back up once the arena is safe.
                    p.health = p.kind.health * 0.5;
                    p.y = FLOOR - 200;
                    p.grounded = false;
                    p.enter(Fighter.State.FREE);
                }
            }
        }
    }

    private Fighter spawn(Fighter.Kind kind, double x) {
        Fighter e = new Fighter(kind, 0, x);
        e.facing = -1;
        e.cooldown = 30 + random.nextInt(40);
        return e;
    }

    private void retryIfWiped() {
        if (beat != Beat.FIGHT) {
            return;
        }
        boolean anyone = false;
        for (Fighter p : pilots) {
            anyone |= p.alive();
        }
        if (anyone) {
            retry = 0;
            return;
        }
        if (++retry < RETRY_TICKS) {
            return;
        }
        // Back to the start of the arena that beat them, everyone on their feet.
        retry = 0;
        double start = segment >= 0 ? SEGMENTS.get(segment).left() + 60 : SHIP_X;
        enemies.clear();
        beams.clear();
        shocks.clear();
        hazards.clear();
        locked = false;
        segment--;
        for (Fighter p : pilots) {
            p.health = p.kind.health;
            p.ki = Math.max(p.ki, 40);
            p.x = start - 200 + p.player * 40;
            p.y = FLOOR;
            p.vx = 0;
            p.vy = 0;
            p.grounded = true;
            p.enter(Fighter.State.FREE);
        }
    }

    private void followCamera() {
        double sum = 0;
        int n = 0;
        for (Fighter p : pilots) {
            if (p.alive()) {
                sum += p.x + p.facing * 90;
                n++;
            }
        }
        double target = (n == 0 ? (beat == Beat.LANDING || beat == Beat.LIFTOFF || beat == Beat.PICKUP ? shipX : camera
                + GameConfig.WIDTH / 2.0) : sum / n) - GameConfig.WIDTH / 2.0;
        if (beat == Beat.PICKUP) {
            target = shipX - GameConfig.WIDTH / 2.0;
        }
        target = Math.max(wallLeft() - 40, Math.min(wallRight() + 40 - GameConfig.WIDTH, target));
        target = Math.max(0, Math.min(LENGTH - GameConfig.WIDTH, target));
        camera += (target - camera) * 0.09;
    }

    private void moveParticles() {
        List<Particle> alive = new ArrayList<>(particles.size());
        for (Particle p : particles) {
            double vy = p.vy() + (p.kind() == Particle.DUST ? -0.02 : p.kind() == Particle.KI ? -0.08 : 0.35);
            double vx = p.vx() * (p.kind() == Particle.DUST ? 0.93 : 0.98);
            double y = p.y() + vy;
            if (p.kind() == Particle.DEBRIS && y > FLOOR) {
                y = FLOOR;
                vy = -vy * 0.4;    // chips skitter rather than vanish into the floor
                vx *= 0.7;
            }
            if (p.life() > 1) {
                alive.add(new Particle(p.x() + vx, y, vx, vy, p.life() - 1, p.maxLife(), p.kind()));
            }
        }
        particles.clear();
        particles.addAll(alive);
        for (Fighter f : pilots) {
            if (f.state == Fighter.State.CHARGE && tick % 2 == 0) {
                double ang = random.nextDouble() * Math.PI * 2;
                double r = 40 + random.nextDouble() * 30;
                particles.add(new Particle(f.x + StrictMath.cos(ang) * r, f.y - f.kind.height * 0.5 + StrictMath.sin(ang) * r,
                        -StrictMath.cos(ang) * 2, -StrictMath.sin(ang) * 2, 18, 18, Particle.KI));
            }
        }
    }

    private void dust(double x, double y, double strength) {
        particles.add(new Particle(x, y - 4, (random.nextDouble() - 0.5) * 3 * strength,
                -random.nextDouble() * 1.4 * strength, 40, 40, Particle.DUST));
    }

    private void debris(double x, double y) {
        particles.add(new Particle(x, y - 6, (random.nextDouble() - 0.5) * 7, -3 - random.nextDouble() * 7,
                50, 50, Particle.DEBRIS));
    }

    /** Folds the whole stage into one number, for the determinism test. */
    public long checksum() {
        long h = tick * 31L + beat.ordinal();
        for (Fighter f : all()) {
            h = h * 1_000_003 + (Double.doubleToLongBits(f.x) ^ Double.doubleToLongBits(f.y) * 7) + (long) (f.health * 100);
        }
        return h;
    }
}
